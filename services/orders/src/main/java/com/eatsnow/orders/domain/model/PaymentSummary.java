package com.eatsnow.orders.domain.model;

import com.eatsnow.common.domain.model.Money;
import java.time.Instant;
import java.util.UUID;

/** PaymentSummary (Spec 3.4): the Orders projection of the payment. */
public record PaymentSummary(UUID orderId, PaymentStatus status, Money total, Instant updatedAt) {
}
