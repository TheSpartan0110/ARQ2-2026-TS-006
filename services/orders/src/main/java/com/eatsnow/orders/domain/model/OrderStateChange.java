package com.eatsnow.orders.domain.model;

import java.util.UUID;

/** One row of the order state history. previous values are null for the initial creation. */
public record OrderStateChange(UUID orderId, OrderStatus fromStatus, OrderStatus toStatus,
                               PaymentStatus fromPaymentStatus, PaymentStatus toPaymentStatus,
                               int orderVersion, UUID causedBy) {
}
