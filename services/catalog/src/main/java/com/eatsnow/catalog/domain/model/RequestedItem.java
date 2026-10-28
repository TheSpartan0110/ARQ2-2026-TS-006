package com.eatsnow.catalog.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * RequestedItem input for quotes (Spec 2.2).
 */
public record RequestedItem(
        UUID menuItemId,
        int quantity
) {
    public RequestedItem {
        Objects.requireNonNull(menuItemId, "menuItemId cannot be null");
        if (quantity < 1 || quantity > 20) {
            throw new IllegalArgumentException("quantity must be between 1 and 20");
        }
    }
}
