package com.eatsnow.notifications.domain.port.out;

import java.util.UUID;

/** Highest processed OrderStatusChanged version per order (Spec 4.2.2). */
public interface OrderVersionPort {
    /** @return true if the version is newer than every processed one (and is now recorded), false if it is stale */
    boolean advance(UUID orderId, int version);
}
