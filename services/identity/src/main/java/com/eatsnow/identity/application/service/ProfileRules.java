package com.eatsnow.identity.application.service;

import com.eatsnow.common.domain.exception.InvalidRequestException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Validation and token rules shared by registration, admin provisioning and activation (Spec 2.2 & 3.2).
 */
final class ProfileRules {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** BCrypt ignores everything after 72 bytes, so longer passwords are rejected rather than silently truncated. */
    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_LENGTH = 72;

    private ProfileRules() {}

    static String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new InvalidRequestException("Invalid email format: " + email);
        }
        return normalized;
    }

    static String validDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank() || displayName.length() > 100) {
            throw new InvalidRequestException("displayName must be between 1 and 100 characters");
        }
        return displayName.trim();
    }

    static void validatePassword(String rawPassword) {
        int length = rawPassword == null ? 0 : rawPassword.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_PASSWORD_LENGTH || length > MAX_PASSWORD_LENGTH) {
            throw new InvalidRequestException("password must be between " + MIN_PASSWORD_LENGTH + " and "
                    + MAX_PASSWORD_LENGTH + " characters");
        }
    }

    /** 256-bit URL-safe random token for activation links. */
    static String newActivationToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
