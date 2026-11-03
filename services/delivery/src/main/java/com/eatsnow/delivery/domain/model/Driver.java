package com.eatsnow.delivery.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Driver (Spec 2.2). Assignable only when onDuty is true and activeDeliveryId is null. lastAssignedAt is internal: the
 * driver with the longest time since the last assignment is chosen first (Spec 3.6).
 */
public record Driver(UUID id, UUID profileId, boolean onDuty, UUID activeDeliveryId, Instant lastAssignedAt,
                     int version) {

    public static Driver offDuty(UUID driverId, UUID profileId) {
        return new Driver(driverId, profileId, false, null, null, 1);
    }

    public boolean assignable() {
        return onDuty && activeDeliveryId == null;
    }
}
