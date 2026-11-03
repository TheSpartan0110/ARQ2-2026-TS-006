package com.eatsnow.payments.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.ProviderResult;
import com.eatsnow.payments.domain.port.out.PaymentEventPublisherPort;
import com.eatsnow.payments.domain.port.out.PaymentRepositoryPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the provider's answer and the event that announces it in ONE transaction: PaymentAuthorized, PaymentFailed
 * or PaymentPendingReconciliation exists only once the outcome is durably stored (Spec 4.3 item 5). It is a separate
 * bean so the provider call that precedes it never runs inside a database transaction.
 */
@Component
public class PaymentOutcomeService {
    private static final Logger log = LoggerFactory.getLogger(PaymentOutcomeService.class);

    private final PaymentRepositoryPort payments;
    private final PaymentEventPublisherPort events;
    private final MeterRegistry registry;

    public PaymentOutcomeService(PaymentRepositoryPort payments, PaymentEventPublisherPort events,
                                 MeterRegistry registry) {
        this.payments = payments;
        this.events = events;
        this.registry = registry;
    }

    @Transactional
    public void apply(Payment payment, ProviderResult result) {
        int newVersion = payment.version() + 1;
        switch (result.outcome()) {
            case AUTHORIZED -> {
                if (payments.markAuthorized(payment, result.providerReference())) {
                    events.paymentAuthorized(payment, newVersion);
                    record("AUTHORIZED");
                }
            }
            case FAILED -> {
                if (payments.markFailed(payment, result.failureCode())) {
                    events.paymentFailed(payment, result.failureCode(), newVersion);
                    record("FAILED");
                }
            }
            case UNKNOWN -> {
                // Only the first timeout emits the event; later reconciliation misses stay silent.
                if (payments.markUnknown(payment)) {
                    events.paymentPendingReconciliation(payment, newVersion);
                    record("UNKNOWN");
                }
            }
        }
    }

    private void record(String outcome) {
        registry.counter("eatsnow.payment.operations", "operation", "CHARGE", "outcome", outcome).increment();
        // UNKNOWN is never treated as proof of a charge or of a refusal (Spec 6.6).
        log.info("Payment outcome recorded", kv("operation", "charge payment"), kv("outcome", "SUCCESS"),
                kv("paymentStatus", outcome));
    }
}
