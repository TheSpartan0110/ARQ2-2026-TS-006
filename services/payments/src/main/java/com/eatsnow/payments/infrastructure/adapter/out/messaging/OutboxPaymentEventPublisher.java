package com.eatsnow.payments.infrastructure.adapter.out.messaging;

import com.eatsnow.messaging.contract.Payloads;
import com.eatsnow.messaging.outbox.OutboxWriter;
import com.eatsnow.messaging.support.MessageTypes;
import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.Refund;
import com.eatsnow.payments.domain.port.out.PaymentEventPublisherPort;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Writes events to the transactional outbox (ADR 001); the relay publishes them after the commit. */
@Component
public class OutboxPaymentEventPublisher implements PaymentEventPublisherPort {
    private static final String PRODUCER = "payments";

    private final OutboxWriter outbox;

    public OutboxPaymentEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void paymentAuthorized(Payment p, int newVersion) {
        publish(p, MessageTypes.PAYMENT_AUTHORIZED, newVersion, new Payloads.PaymentAuthorized(p.id(), p.total()));
    }

    @Override
    public void paymentFailed(Payment p, String failureCode, int newVersion) {
        publish(p, MessageTypes.PAYMENT_FAILED, newVersion, new Payloads.PaymentFailed(p.id(), p.total(), failureCode));
    }

    @Override
    public void paymentPendingReconciliation(Payment p, int newVersion) {
        publish(p, MessageTypes.PAYMENT_PENDING_RECONCILIATION, newVersion,
                new Payloads.PaymentPendingReconciliation(p.id(), "CHARGE", Instant.now()));
    }

    @Override
    public void paymentRefunded(Refund r, int paymentVersion) {
        outbox.enqueue(MessageTypes.EVENTS, MessageTypes.PAYMENT_REFUNDED, PRODUCER, r.orderId(), r.correlationId(),
                r.commandMessageId(), r.paymentId(), paymentVersion,
                new Payloads.PaymentRefunded(r.paymentId(), r.amount()));
    }

    @Override
    public void refundPendingReconciliation(Refund r, int paymentVersion) {
        outbox.enqueue(MessageTypes.EVENTS, MessageTypes.PAYMENT_PENDING_RECONCILIATION, PRODUCER, r.orderId(),
                r.correlationId(), r.commandMessageId(), r.paymentId(), paymentVersion,
                new Payloads.PaymentPendingReconciliation(r.paymentId(), "REFUND", Instant.now()));
    }

    private void publish(Payment p, String messageType, int version, Object payload) {
        outbox.enqueue(MessageTypes.EVENTS, messageType, PRODUCER, p.orderId(), p.correlationId(),
                p.commandMessageId(), p.id(), version, payload);
    }
}
