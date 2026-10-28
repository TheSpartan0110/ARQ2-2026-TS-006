package com.eatsnow.identity.infrastructure.adapter.in.web;

import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.common.web.dto.PaginatedResponseDto;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.in.AdminProfileUseCase;
import com.eatsnow.identity.infrastructure.adapter.in.web.dto.CreateProfileRequestDto;
import com.eatsnow.identity.infrastructure.adapter.in.web.dto.ProfileResponseDto;
import com.eatsnow.identity.infrastructure.adapter.in.web.dto.UpdateProfileStatusRequestDto;
import com.eatsnow.identity.infrastructure.config.security.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller exposing administrative profile management endpoints (Spec 3.2).
 */
@RestController
@RequestMapping("/api/v1/admin/profiles")
public class AdminProfileController {

    private final AdminProfileUseCase adminProfileUseCase;
    private final ObjectMapper objectMapper;

    public AdminProfileController(AdminProfileUseCase adminProfileUseCase, ObjectMapper objectMapper) {
        this.adminProfileUseCase = adminProfileUseCase;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<PaginatedResponseDto<ProfileResponseDto>> listProfiles(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "role", required = false) Role role
    ) {
        PageResult<Profile> pageResult = adminProfileUseCase.listProfiles(limit, cursor, role);
        List<ProfileResponseDto> items = pageResult.items().stream()
                .map(ProfileResponseDto::fromDomain)
                .toList();

        return ResponseEntity.ok(new PaginatedResponseDto<>(items, pageResult.nextCursor()));
    }

    @PostMapping
    public ResponseEntity<ProfileResponseDto> createProfile(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateProfileRequestDto request,
            Authentication authentication
    ) {
        UUID adminId = extractAdminId(authentication);
        String rawPayload = serializePayload(request);
        Profile profile = adminProfileUseCase.createProfile(
                adminId,
                request.displayName(),
                request.email(),
                request.roles(),
                request.restaurantId(),
                idempotencyKey,
                rawPayload
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponseDto.fromDomain(profile));
    }

    @PatchMapping("/{profileId}")
    public ResponseEntity<ProfileResponseDto> updateProfileStatus(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable("profileId") UUID profileId,
            @Valid @RequestBody UpdateProfileStatusRequestDto request,
            Authentication authentication
    ) {
        UUID adminId = extractAdminId(authentication);
        String rawPayload = serializePayload(request);
        Profile profile = adminProfileUseCase.updateProfileStatus(
                adminId,
                profileId,
                request.active(),
                idempotencyKey,
                rawPayload
        );

        return ResponseEntity.ok(ProfileResponseDto.fromDomain(profile));
    }

    private UUID extractAdminId(Authentication authentication) {
        if (authentication == null) {
            throw new InvalidRequestException("Missing authenticated principal");
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Cannot parse admin subject UUID: " + authentication.getName());
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
