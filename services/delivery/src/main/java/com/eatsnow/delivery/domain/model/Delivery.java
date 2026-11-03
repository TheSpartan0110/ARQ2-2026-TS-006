package com.eatsnow.delivery.domain.model;

import com.eatsnow.common.domain.exception.InvalidTransitionException;
import java.time.Instant;
import java.util.UUID;

/**
 * Delivery aggregate (Spec 2.2, 3.6). Immutable: each transition returns a new instance with version + 1.
 * Allowed: UNASSIGNED -> ASSIGNED (system), ASSIGNED -> PICKED_UP, PICKED_UP -> DELIVERED (driver).
 */
public record Delivery(
        UUID id,
        UUID orderId,
        UUID restaurantId,
        UUID driverId,
        DeliveryAddress deliveryAddress,
        DeliveryStatus status,
        UUID correlationId,
        UUID commandMessageId,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public static Delivery unassigned(UUID id, UUID orderId, UUID restaurantId, DeliveryAddress address,
                                      UUID correlationId, UUID commandMessageId, Instant now) {
        return new Delivery(id, orderId, restaurantId, null, address, DeliveryStatus.UNASSIGNED, correlationId,
                commandMessageId, 1, now, now);
    }

    public Delivery assignTo(UUID newDriverId, Instant now) {
        require(DeliveryStatus.UNASSIGNED, "assign");
        return next(DeliveryStatus.ASSIGNED, newDriverId, now);
    }

    public Delivery pickUp(Instant now) {
        require(DeliveryStatus.ASSIGNED, "pick up");
        return next(DeliveryStatus.PICKED_UP, driverId, now);
    }

    public Delivery complete(Instant now) {
        require(DeliveryStatus.PICKED_UP, "complete");
        return next(DeliveryStatus.DELIVERED, driverId, now);
    }

    private void require(DeliveryStatus expected, String action) {
        if (status != expected) {
            throw new InvalidTransitionException("Cannot " + action + " a delivery that is " + status, status.name());
        }
    }

    private Delivery next(DeliveryStatus newStatus, UUID newDriverId, Instant now) {
        return new Delivery(id, orderId, restaurantId, newDriverId, deliveryAddress, newStatus, correlationId,
                commandMessageId, version + 1, createdAt, now);
    }
}
