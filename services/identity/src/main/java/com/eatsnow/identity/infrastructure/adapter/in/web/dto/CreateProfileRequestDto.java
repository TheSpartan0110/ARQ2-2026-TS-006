package com.eatsnow.identity.infrastructure.adapter.in.web.dto;

import com.eatsnow.common.domain.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Request payload for administrator profile creation (Spec 3.2).
 */
public record CreateProfileRequestDto(
        @NotBlank(message = "displayName is required")
        @Size(min = 1, max = 100, message = "displayName must be between 1 and 100 characters")
        String displayName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @NotEmpty(message = "roles cannot be empty")
        @Size(min = 1, max = 4, message = "roles must contain between 1 and 4 roles")
        Set<Role> roles,

        UUID restaurantId
) {
}
