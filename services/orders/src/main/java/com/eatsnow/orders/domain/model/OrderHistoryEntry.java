package com.eatsnow.orders.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One recorded change of an order's status or payment status, as read back for GET /orders/{id}/history (Spec 3.4).
 * Previous values are null for the initial creation; causedBy is the messageId that triggered the change, or null for
 * the HTTP creation.
 */
public record OrderHistoryEntry(OrderStatus fromStatus, OrderStatus toStatus,
                                PaymentStatus fromPaymentStatus, PaymentStatus toPaymentStatus,
                                int orderVersion, UUID causedBy, Instant occurredAt) {
}
