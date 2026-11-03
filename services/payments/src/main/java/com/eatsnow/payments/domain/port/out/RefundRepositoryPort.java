package com.eatsnow.payments.domain.port.out;

import com.eatsnow.payments.domain.model.Refund;
import com.eatsnow.payments.domain.model.RefundStatus;
import java.util.List;

public interface RefundRepositoryPort {
    /** @return false if a refund already exists for the payment */
    boolean insertPending(Refund refund);

    List<Refund> findByStatus(RefundStatus status, int limit);

    /** Conditional updates (version and open status): at most one definitive outcome is applied. */
    boolean markRefunded(Refund refund, String providerReference);

    boolean markFailed(Refund refund, String failureCode);

    boolean markUnknown(Refund refund);

    /** Age in seconds of the oldest refund that is PENDING, UNKNOWN or FAILED, 0 if none. */
    long oldestUnresolvedSeconds();
}
