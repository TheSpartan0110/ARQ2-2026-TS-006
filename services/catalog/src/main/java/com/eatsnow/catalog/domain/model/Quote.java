package com.eatsnow.catalog.domain.model;

import com.eatsnow.common.domain.model.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Quote business object (Spec 2.2).
 * Expiry is 120 seconds after creation (Spec 2.3).
 */
public record Quote(
        UUID id,
        UUID customerId,
        UUID restaurantId,
        List<OrderItem> items,
        Money total,
        Instant expiresAt,
        String quoteToken
) {
    public Quote {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(customerId, "customerId cannot be null");
        Objects.requireNonNull(restaurantId, "restaurantId cannot be null");
        Objects.requireNonNull(items, "items cannot be null");
        if (items.isEmpty() || items.size() > 50) {
            throw new IllegalArgumentException("items size must be between 1 and 50");
        }
        Objects.requireNonNull(total, "total cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        Objects.requireNonNull(quoteToken, "quoteToken cannot be null");
    }
}
