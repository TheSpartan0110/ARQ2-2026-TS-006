package com.eatsnow.orders.domain.port.out;

import com.eatsnow.orders.domain.model.OrderEvent;
import com.eatsnow.orders.domain.model.PendingEvent;
import java.util.List;
import java.util.UUID;

/** Durable store of premature events (Spec 4.3 item 3). Written inside the consumer's transaction. */
public interface PendingEventStorePort {
    void store(OrderEvent event);

    /** Pending events of one order in arrival order. Quarantined events are not returned. */
    List<PendingEvent> findPending(UUID orderId);

    void delete(long id);

    /** The event contradicts the order: it is kept for investigation but never retried again. */
    void quarantine(long id);

    List<UUID> orderIdsWithPending(int limit);

    /** Age in seconds of the oldest pending event, 0 if none (feeds PrerequisiteWaitTooLong). */
    long oldestPendingSeconds();
}
