package com.eatsnow.payments.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.ProviderResult;
import com.eatsnow.payments.domain.model.Refund;
import com.eatsnow.payments.domain.port.out.PaymentEventPublisherPort;
import com.eatsnow.payments.domain.port.out.PaymentRepositoryPort;
import com.eatsnow.payments.domain.port.out.RefundRepositoryPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the provider's refund answer and its event in ONE transaction (Spec 4.3 items 5 and 6):
 * REFUNDED -> payment REFUNDED and PaymentRefunded; timeout -> PaymentPendingReconciliation(REFUND) while the payment
 * stays REFUND_PENDING (never regressed to UNKNOWN); permanent refusal -> alert metric and no event, because the
 * customer must never be reported as refunded without confirmation.
 */
@Component
public class RefundOutcomeService {
    private static final Logger log = LoggerFactory.getLogger(RefundOutcomeService.class);

    private final PaymentRepositoryPort payments;
    private final RefundRepositoryPort refunds;
    private final PaymentEventPublisherPort events;
    private final MeterRegistry registry;

    public RefundOutcomeService(PaymentRepositoryPort payments, RefundRepositoryPort refunds,
                                PaymentEventPublisherPort events, MeterRegistry registry) {
        this.payments = payments;
        this.refunds = refunds;
        this.events = events;
        this.registry = registry;
    }

    @Transactional
    public void apply(Refund refund, ProviderResult result) {
        switch (result.outcome()) {
            case REFUNDED -> {
                Payment payment = payments.findById(refund.paymentId()).orElseThrow();
                if (refunds.markRefunded(refund, result.providerReference()) && payments.markRefunded(payment)) {
                    events.paymentRefunded(refund, payment.version() + 1);
                    record("REFUNDED");
                }
            }
            case UNKNOWN -> {
                if (refunds.markUnknown(refund)) {
                    Payment payment = payments.findById(refund.paymentId()).orElseThrow();
                    events.refundPendingReconciliation(refund, payment.version());
                    record("UNKNOWN");
                }
            }
            case FAILED -> {
                if (refunds.markFailed(refund, result.failureCode())) {
                    record("FAILED");
                    log.error("Refund permanently refused by the provider: manual reconciliation required",
                            kv("operation", "refund payment"), kv("outcome", "FAILURE"),
                            kv("errorCode", result.failureCode()));
                }
            }
            default -> { }
        }
    }

    private void record(String outcome) {
        registry.counter("eatsnow.payment.operations", "operation", "REFUND", "outcome", outcome).increment();
        if (!"FAILED".equals(outcome)) {
            log.info("Refund outcome recorded", kv("operation", "refund payment"), kv("outcome", "SUCCESS"),
                    kv("paymentStatus", outcome));
        }
    }
}
