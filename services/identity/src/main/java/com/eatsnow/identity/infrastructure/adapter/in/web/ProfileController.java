package com.eatsnow.identity.infrastructure.adapter.in.web;

import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.in.GetProfileUseCase;
import com.eatsnow.identity.domain.port.in.UpdateMyProfileUseCase;
import com.eatsnow.identity.infrastructure.adapter.in.web.dto.ProfileResponseDto;
import com.eatsnow.identity.infrastructure.adapter.in.web.dto.UpdateMyProfileRequestDto;
import com.eatsnow.identity.infrastructure.config.security.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller exposing self-service profile endpoints (Spec 3.2).
 */
@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    private final GetProfileUseCase getProfileUseCase;
    private final UpdateMyProfileUseCase updateMyProfileUseCase;
    private final ObjectMapper objectMapper;

    public ProfileController(
            GetProfileUseCase getProfileUseCase,
            UpdateMyProfileUseCase updateMyProfileUseCase,
            ObjectMapper objectMapper
    ) {
        this.getProfileUseCase = getProfileUseCase;
        this.updateMyProfileUseCase = updateMyProfileUseCase;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponseDto> getMyProfile(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        Profile profile = getProfileUseCase.getProfileById(userId);
        return ResponseEntity.ok(ProfileResponseDto.fromDomain(profile));
    }

    @PatchMapping("/me")
    public ResponseEntity<ProfileResponseDto> updateMyDisplayName(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UpdateMyProfileRequestDto request,
            Authentication authentication
    ) {
        UUID userId = extractUserId(authentication);
        String rawPayload = serializePayload(request);
        Profile updated = updateMyProfileUseCase.updateDisplayName(userId, request.displayName(), idempotencyKey, rawPayload);
        return ResponseEntity.ok(ProfileResponseDto.fromDomain(updated));
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null) {
            throw new InvalidRequestException("Missing authenticated principal");
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Cannot parse authenticated subject UUID: " + authentication.getName());
        }
    }

    private String serializePayload(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }
}
