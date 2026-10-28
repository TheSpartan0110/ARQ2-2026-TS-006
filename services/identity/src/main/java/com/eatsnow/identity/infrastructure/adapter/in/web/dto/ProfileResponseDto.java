package com.eatsnow.identity.infrastructure.adapter.in.web.dto;

import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.Profile;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Set;
import java.util.UUID;

/**
 * Standard Profile representation conforming to Spec 2.2 & 3.2.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResponseDto(
        UUID id,
        String displayName,
        String email,
        Set<Role> roles,
        UUID restaurantId,
        UUID driverId,
        boolean active
) {
    public static ProfileResponseDto fromDomain(Profile profile) {
        return new ProfileResponseDto(
                profile.id(),
                profile.displayName(),
                profile.email(),
                profile.roles(),
                profile.restaurantId(),
                profile.driverId(),
                profile.active()
        );
    }
}
