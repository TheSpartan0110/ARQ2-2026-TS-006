package com.eatsnow.payments.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.payments.domain.exception.InconsistentCommandException;
import com.eatsnow.payments.domain.exception.PaymentNotReadyException;
import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.PaymentStatus;
import com.eatsnow.payments.domain.model.Refund;
import com.eatsnow.payments.domain.model.RefundStatus;
import com.eatsnow.payments.domain.port.in.RecordRefundUseCase;
import com.eatsnow.payments.domain.port.out.PaymentRepositoryPort;
import com.eatsnow.payments.domain.port.out.RefundRepositoryPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the refund attempt BEFORE any provider call and marks the payment REFUND_PENDING (Spec 4.3 items 5 and 6).
 * One logical refund per payment: the unique paymentId stops a duplicate command that carries a new messageId.
 */
@Service
public class RefundCommandService implements RecordRefundUseCase {
    private static final Logger log = LoggerFactory.getLogger(RefundCommandService.class);

    private final PaymentRepositoryPort payments;
    private final RefundRepositoryPort refunds;

    public RefundCommandService(PaymentRepositoryPort payments, RefundRepositoryPort refunds) {
        this.payments = payments;
        this.refunds = refunds;
    }

    @Override
    @Transactional
    public boolean recordRefund(Command command) {
        Payment payment = payments.findById(command.paymentId())
                .orElseThrow(() -> new InconsistentCommandException("Unknown payment " + command.paymentId()));
        if (!payment.orderId().equals(command.orderId()) || !payment.total().equals(command.amount())) {
            throw new InconsistentCommandException("Refund order or amount does not match the authorized payment");
        }
        switch (payment.status()) {
            case AUTHORIZED, REFUND_PENDING, REFUNDED -> { }
            case PENDING, UNKNOWN -> throw new PaymentNotReadyException("The charge has no definitive outcome yet");
            default -> throw new InconsistentCommandException("Cannot refund a payment in state " + payment.status());
        }

        Refund refund = new Refund(UUID.randomUUID(), payment.id(), payment.orderId(), command.amount(),
                "refund-" + payment.id(),                      // derived from the paymentId (Spec 4.2.2)
                payment.idempotencyKey(), command.correlationId(), command.commandMessageId(),
                command.traceparent(), RefundStatus.PENDING, 1);
        if (!refunds.insertPending(refund)) {
            log.info("Refund already recorded for the payment", kv("operation", "record refund"),
                    kv("outcome", "DUPLICATE"));
            return false;
        }
        if (payment.status() == PaymentStatus.AUTHORIZED) {
            payments.markRefundPending(payment);
        }
        return true;
    }
}
