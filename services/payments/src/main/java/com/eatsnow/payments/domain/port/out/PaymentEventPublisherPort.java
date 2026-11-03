package com.eatsnow.payments.domain.port.out;

import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.Refund;

/** Outgoing events, written to the transactional outbox inside the caller's transaction (ADR 001). */
public interface PaymentEventPublisherPort {
    void paymentAuthorized(Payment payment, int newVersion);

    void paymentFailed(Payment payment, String failureCode, int newVersion);

    void paymentPendingReconciliation(Payment payment, int newVersion);

    /** Emitted only after the refund is confirmed and durably recorded. */
    void paymentRefunded(Refund refund, int paymentVersion);

    /** A refund timeout: informational, never regresses the payment (Spec 4.3 item 6). */
    void refundPendingReconciliation(Refund refund, int paymentVersion);
}
