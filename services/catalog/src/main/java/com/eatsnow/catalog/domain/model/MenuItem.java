package com.eatsnow.catalog.domain.model;

import com.eatsnow.common.domain.model.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * MenuItem business object (Spec 2.2).
 */
public record MenuItem(
        UUID id,
        UUID restaurantId,
        String name,
        Money price,
        boolean available,
        int version
) {
    public MenuItem {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(restaurantId, "restaurantId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(price, "price cannot be null");
        if (name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("name must be between 1 and 120 characters");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be a positive integer");
        }
    }
}
