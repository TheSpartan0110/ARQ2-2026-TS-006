package com.eatsnow.identity.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for self-updating display name (Spec 3.2).
 * Roles, email, and ownership links cannot be modified here.
 */
public record UpdateMyProfileRequestDto(
        @NotBlank(message = "displayName is required")
        @Size(min = 1, max = 100, message = "displayName must be between 1 and 100 characters")
        String displayName
) {
}
