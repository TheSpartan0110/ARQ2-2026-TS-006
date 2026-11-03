package com.eatsnow.orders.domain.model;

import com.eatsnow.common.domain.model.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Content of a quote whose signature, expiry, customer binding and totals were verified (Spec 2.3). */
public record Quote(UUID id, UUID customerId, UUID restaurantId, List<OrderItem> items, Money total, Instant expiresAt) {
}
