package com.eatsnow.restaurants.domain.model;

import com.eatsnow.common.domain.exception.InvalidTransitionException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A restaurant's view of a paid order (Spec 2.2, 3.5). Immutable: each local transition returns a new instance with
 * version + 1. Allowed transitions: PENDING -> ACCEPTED, PENDING -> REJECTED, ACCEPTED -> PREPARING.
 */
public record RestaurantOrder(
        UUID orderId,
        UUID restaurantId,
        List<OrderLine> items,
        RestaurantOrderStatus status,
        String reason,
        UUID correlationId,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public static RestaurantOrder pending(UUID orderId, UUID restaurantId, List<OrderLine> items, UUID correlationId,
                                          Instant now) {
        return new RestaurantOrder(orderId, restaurantId, items, RestaurantOrderStatus.PENDING, null, correlationId,
                1, now, now);
    }

    public RestaurantOrder accept(Instant now) {
        require(RestaurantOrderStatus.PENDING, "accept");
        return next(RestaurantOrderStatus.ACCEPTED, null, now);
    }

    public RestaurantOrder reject(String rejectionReason, Instant now) {
        require(RestaurantOrderStatus.PENDING, "reject");
        return next(RestaurantOrderStatus.REJECTED, rejectionReason, now);
    }

    public RestaurantOrder startPreparation(Instant now) {
        require(RestaurantOrderStatus.ACCEPTED, "start preparation of");
        return next(RestaurantOrderStatus.PREPARING, null, now);
    }

    private void require(RestaurantOrderStatus expected, String action) {
        if (status != expected) {
            throw new InvalidTransitionException("Cannot " + action + " a restaurant order that is " + status,
                    status.name());
        }
    }

    private RestaurantOrder next(RestaurantOrderStatus newStatus, String newReason, Instant now) {
        return new RestaurantOrder(orderId, restaurantId, items, newStatus, newReason, correlationId, version + 1,
                createdAt, now);
    }
}
