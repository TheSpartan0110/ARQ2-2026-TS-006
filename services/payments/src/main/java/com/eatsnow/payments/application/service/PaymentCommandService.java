package com.eatsnow.payments.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.PaymentStatus;
import com.eatsnow.payments.domain.port.in.RecordChargeUseCase;
import com.eatsnow.payments.domain.port.out.PaymentRepositoryPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the charge attempt as PENDING BEFORE any provider call (Spec 4.3 item 5). The unique order id guarantees
 * one logical payment per order even when a duplicate command arrives with a new messageId.
 */
@Service
public class PaymentCommandService implements RecordChargeUseCase {
    private static final Logger log = LoggerFactory.getLogger(PaymentCommandService.class);

    private final PaymentRepositoryPort payments;

    public PaymentCommandService(PaymentRepositoryPort payments) {
        this.payments = payments;
    }

    @Override
    @Transactional
    public boolean recordCharge(Command command) {
        Payment payment = new Payment(UUID.randomUUID(), command.orderId(), command.total(),
                "charge-" + command.orderId(),                    // provider idempotency key derived from the order
                command.encryptedPaymentToken(), command.correlationId(), command.commandMessageId(),
                command.traceparent(), PaymentStatus.PENDING, 1);
        boolean recorded = payments.insertPending(payment);
        if (!recorded) {
            log.info("Charge already recorded for the order", kv("operation", "record charge"),
                    kv("outcome", "DUPLICATE"));
        }
        return recorded;
    }
}
