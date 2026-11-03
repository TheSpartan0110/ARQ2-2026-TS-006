package com.eatsnow.orders.domain.model;

import com.eatsnow.common.domain.model.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Order aggregate (Spec 2.2). Immutable: a state change produces a new instance with version + 1.
 * paymentId, deliveryId and driverId are references learned from consumed events (null until then).
 */
public record Order(
        UUID id,
        UUID customerId,
        UUID restaurantId,
        UUID quoteId,
        List<OrderItem> items,
        Money total,
        DeliveryAddress deliveryAddress,
        OrderStatus status,
        PaymentStatus paymentStatus,
        UUID correlationId,
        UUID paymentId,
        UUID deliveryId,
        UUID driverId,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public static Order create(UUID id, Quote quote, DeliveryAddress address, UUID correlationId, Instant now) {
        return new Order(id, quote.customerId(), quote.restaurantId(), quote.id(), quote.items(), quote.total(),
                address, OrderStatus.CREATED, PaymentStatus.PENDING, correlationId, null, null, null, 1, now, now);
    }

    public Order withStates(OrderStatus newStatus, PaymentStatus newPaymentStatus, Instant now) {
        return new Order(id, customerId, restaurantId, quoteId, items, total, deliveryAddress, newStatus,
                newPaymentStatus, correlationId, paymentId, deliveryId, driverId, version + 1, createdAt, now);
    }

    /** Copy that remembers the payment id; the version is bumped later by withStates. */
    public Order withPaymentId(UUID newPaymentId) {
        return new Order(id, customerId, restaurantId, quoteId, items, total, deliveryAddress, status, paymentStatus,
                correlationId, newPaymentId, deliveryId, driverId, version, createdAt, updatedAt);
    }

    /** New version that records the assigned delivery and driver; status and paymentStatus do not change. */
    public Order withDelivery(UUID newDeliveryId, UUID newDriverId, Instant now) {
        return new Order(id, customerId, restaurantId, quoteId, items, total, deliveryAddress, status, paymentStatus,
                correlationId, paymentId, newDeliveryId, newDriverId, version + 1, createdAt, now);
    }
}
