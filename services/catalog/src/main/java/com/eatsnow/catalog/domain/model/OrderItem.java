package com.eatsnow.catalog.domain.model;

import com.eatsnow.common.domain.model.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * OrderItem immutable snapshot for quotes and orders (Spec 2.2).
 */
public record OrderItem(
        UUID menuItemId,
        String name,
        int quantity,
        Money unitPrice
) {
    public OrderItem {
        Objects.requireNonNull(menuItemId, "menuItemId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(unitPrice, "unitPrice cannot be null");
        if (quantity < 1 || quantity > 20) {
            throw new IllegalArgumentException("quantity must be between 1 and 20");
        }
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }
}
