package com.eatsnow.catalog.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Restaurant business object (Spec 2.2).
 */
public record Restaurant(
        UUID id,
        String name,
        boolean available
) {
    public Restaurant {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        if (name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("name must be between 1 and 120 characters");
        }
    }
}
