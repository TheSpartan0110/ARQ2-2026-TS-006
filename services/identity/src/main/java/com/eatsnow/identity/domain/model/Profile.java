package com.eatsnow.identity.domain.model;

import com.eatsnow.common.domain.model.Role;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Profile business object (Spec 2.2 & 3.2).
 */
public record Profile(
        UUID id,
        String displayName,
        String email,
        Set<Role> roles,
        UUID restaurantId,
        UUID driverId,
        String passwordHash,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public Profile {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(email, "email cannot be null");
        Objects.requireNonNull(roles, "roles cannot be null");
        roles = Collections.unmodifiableSet(roles);
    }

    public Profile withDisplayName(String newDisplayName, Instant updateTime) {
        return new Profile(id, newDisplayName, email, roles, restaurantId, driverId, passwordHash, active, createdAt, updateTime);
    }

    public Profile withActive(boolean newActive, Instant updateTime) {
        return new Profile(id, displayName, email, roles, restaurantId, driverId, passwordHash, newActive, createdAt, updateTime);
    }

    public Profile withPasswordHash(String newPasswordHash, Instant updateTime) {
        return new Profile(id, displayName, email, roles, restaurantId, driverId, newPasswordHash, active, createdAt, updateTime);
    }

    /** Accounts created by an ADMIN have no password until activated (Spec 3.2). */
    public boolean isActivated() {
        return passwordHash != null;
    }

    /** Only active, activated profiles may log in or obtain tokens (Spec 3.2). */
    public boolean canLogIn() {
        return active && isActivated();
    }
}
