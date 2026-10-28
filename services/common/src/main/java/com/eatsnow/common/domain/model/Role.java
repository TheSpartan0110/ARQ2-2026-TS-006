package com.eatsnow.common.domain.model;

/**
 * Standard roles for EatsNow platform users (Spec 2.1).
 */
public enum Role {
    USER,
    RESTAURANT,
    DRIVER,
    ADMIN;

    public static Role fromString(String roleStr) {
        if (roleStr == null) return null;
        return Role.valueOf(roleStr.trim().toUpperCase());
    }
}
