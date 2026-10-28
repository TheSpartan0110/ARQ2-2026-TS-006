package com.eatsnow.common.domain.model;

import java.time.Instant;

/**
 * Idempotency tracking record (Spec 3.1).
 * Used across all services for mutating endpoints requiring Idempotency-Key.
 */
public record IdempotencyEntry(
        String scopeKey,
        String requestHash,
        String status, // "IN_PROGRESS", "COMPLETED"
        int statusCode,
        String responseBody,
        Instant createdAt
) {
    public boolean isInProgress() {
        return "IN_PROGRESS".equalsIgnoreCase(status);
    }

    public boolean isCompleted() {
        return "COMPLETED".equalsIgnoreCase(status);
    }
}
