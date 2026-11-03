package com.eatsnow.payments.domain.port.out;

import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepositoryPort {
    /** @return false if a payment already exists for the order */
    boolean insertPending(Payment payment);

    List<Payment> findByStatus(PaymentStatus status, int limit);

    /*
     * Conditional updates (version and current status): at most one definitive outcome is ever applied.
     * Each returns false when the payment changed in the meantime and nothing was written.
     */
    boolean markAuthorized(Payment payment, String providerReference);

    boolean markFailed(Payment payment, String failureCode);

    boolean markUnknown(Payment payment);

    long oldestUnresolvedSeconds();

    Optional<Payment> findById(UUID paymentId);

    /** AUTHORIZED -> REFUND_PENDING when a refund starts. */
    boolean markRefundPending(Payment payment);

    /** REFUND_PENDING -> REFUNDED when the provider confirms the refund. */
    boolean markRefunded(Payment payment);
}
