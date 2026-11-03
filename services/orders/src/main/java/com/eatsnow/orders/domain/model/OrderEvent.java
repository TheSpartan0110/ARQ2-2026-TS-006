package com.eatsnow.orders.domain.model;

import com.eatsnow.common.domain.model.Money;
import java.util.UUID;

/**
 * Events of other services that move the order through its state machine (Spec 2.4). They can arrive early or out
 * of order, so each one is evaluated against the current order state and may be stored as pending (Spec 4.3 item 3).
 */
public sealed interface OrderEvent {

    UUID orderId();

    UUID messageId();

    record RestaurantAccepted(UUID orderId, UUID messageId, UUID restaurantId) implements OrderEvent { }

    record RestaurantRejected(UUID orderId, UUID messageId, UUID restaurantId, String reason) implements OrderEvent { }

    record RestaurantPreparing(UUID orderId, UUID messageId, UUID restaurantId) implements OrderEvent { }

    record DeliveryAssigned(UUID orderId, UUID messageId, UUID deliveryId, UUID driverId) implements OrderEvent { }

    record DeliveryPickedUp(UUID orderId, UUID messageId, UUID deliveryId, UUID driverId) implements OrderEvent { }

    record DeliveryCompleted(UUID orderId, UUID messageId, UUID deliveryId, UUID driverId) implements OrderEvent { }

    record PaymentRefunded(UUID orderId, UUID messageId, UUID paymentId, Money amount) implements OrderEvent { }
}
