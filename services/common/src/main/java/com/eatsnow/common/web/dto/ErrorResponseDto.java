package com.eatsnow.common.web.dto;

import java.util.UUID;

/**
 * Standard error response schema shared by all EatsNow HTTP endpoints (Spec 2.1 & 3.1).
 */
public record ErrorResponseDto(
        String code,
        String message,
        UUID correlationId,
        Object details
) {
    public ErrorResponseDto(String code, String message, UUID correlationId) {
        this(code, message, correlationId, null);
    }
}
