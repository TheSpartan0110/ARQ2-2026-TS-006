package com.eatsnow.identity.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for administrative profile activation/deactivation (Spec 3.2).
 */
public record UpdateProfileStatusRequestDto(
        @NotNull(message = "active status is required")
        Boolean active
) {
}
