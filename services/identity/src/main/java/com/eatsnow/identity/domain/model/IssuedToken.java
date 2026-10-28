package com.eatsnow.identity.domain.model;

import com.eatsnow.common.domain.model.Role;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Encapsulates an issued JWT access token and its metadata (Spec 3.1 & ADR 005).
 */
public record IssuedToken(
        String token,
        String tokenType,
        long expiresIn,
        UUID sub,
        Set<Role> roles,
        UUID restaurantId,
        UUID driverId
) {
    public IssuedToken {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(tokenType, "tokenType cannot be null");
        Objects.requireNonNull(sub, "sub cannot be null");
        Objects.requireNonNull(roles, "roles cannot be null");
    }
}
