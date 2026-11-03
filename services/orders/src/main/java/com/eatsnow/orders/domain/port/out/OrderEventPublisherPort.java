package com.eatsnow.orders.domain.port.out;

import com.eatsnow.orders.domain.model.Order;
import com.eatsnow.orders.domain.model.OrderStatus;
import java.util.UUID;

/**
 * Outgoing messages, written to the transactional outbox inside the caller's transaction (ADR 001).
 * Nothing is sent to the broker from here.
 */
public interface OrderEventPublisherPort {
    void orderCreated(Order order);

    /** Emitted with every change of status or paymentStatus (the only message Notifications consumes). */
    void orderStatusChanged(Order order, OrderStatus previousStatus, UUID causationId);

    void chargePayment(Order order, String encryptedPaymentToken, UUID causationId);

    /** Sent once after PaymentAuthorized (Spec 2.4). */
    void requestRestaurantAcceptance(Order order, UUID causationId);

    /** Sent once when a restaurant rejects a paid order; the amount is the authorized total. */
    void refundPayment(Order order, UUID causationId);

    /** Sent once after RestaurantPreparing; carries the delivery address only in this command. */
    void assignDelivery(Order order, UUID causationId);
}
