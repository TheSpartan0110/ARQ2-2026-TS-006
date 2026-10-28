package com.eatsnow.identity.application.service;

import com.eatsnow.common.domain.exception.EmailAlreadyRegisteredException;
import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.IdempotencyConflictException;
import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.common.domain.exception.InvalidRoleLinkException;
import com.eatsnow.common.domain.exception.InvalidTransitionException;
import com.eatsnow.common.domain.exception.RequestInProgressException;
import com.eatsnow.common.domain.model.IdempotencyEntry;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.in.AdminProfileUseCase;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service managing administrative operations on user profiles (Spec 3.2).
 */
@Service
public class AdminProfileService implements AdminProfileUseCase {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    private final ProfileRepositoryPort profileRepository;
    private final IdempotencyStoragePort idempotencyStorage;
    private final PasswordEncoder passwordEncoder;

    public AdminProfileService(
            ProfileRepositoryPort profileRepository,
            IdempotencyStoragePort idempotencyStorage,
            PasswordEncoder passwordEncoder
    ) {
        this.profileRepository = profileRepository;
        this.idempotencyStorage = idempotencyStorage;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Profile> listProfiles(int limit, String cursor, Role roleFilter) {
        int boundedLimit = Math.max(1, Math.min(limit, 100)); // Default 20, max 100 (Spec 3.1)
        return profileRepository.findAll(boundedLimit, cursor, roleFilter);
    }

    @Override
    @Transactional
    public Profile createProfile(
            UUID adminId,
            String displayName,
            String email,
            Set<Role> roles,
            UUID restaurantId,
            String idempotencyKey,
            String rawBody
    ) {
        Objects.requireNonNull(adminId, "adminId cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(email, "email cannot be null");
        Objects.requireNonNull(roles, "roles cannot be null");

        if (displayName.isBlank() || displayName.length() > 100) {
            throw new InvalidRequestException("displayName must be between 1 and 100 characters");
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new InvalidRequestException("Invalid email format: " + email);
        }

        if (roles.isEmpty() || roles.size() > 4) {
            throw new InvalidRequestException("roles must contain between 1 and 4 roles");
        }

        // Spec 3.2: Role linkage rules
        boolean isRestaurant = roles.contains(Role.RESTAURANT);
        boolean isDriver = roles.contains(Role.DRIVER);

        if (isRestaurant && restaurantId == null) {
            throw new InvalidRoleLinkException("RESTAURANT role requires a valid restaurantId");
        }

        if (restaurantId != null && !isRestaurant) {
            throw new InvalidRoleLinkException("restaurantId provided without RESTAURANT role");
        }

        UUID driverId = isDriver ? UUID.randomUUID() : null;

        // Spec 3.2: Duplicate email returns 409 EMAIL_ALREADY_REGISTERED
        if (profileRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException("Email already registered: " + normalizedEmail);
        }

        String requestHash = computeSha256(rawBody != null ? rawBody : (displayName + ":" + normalizedEmail));
        String scopeKey = adminId + ":POST:/api/v1/admin/profiles:" + (idempotencyKey != null ? idempotencyKey : UUID.randomUUID());

        if (idempotencyKey != null) {
            Optional<IdempotencyEntry> existing = idempotencyStorage.find(scopeKey);
            if (existing.isPresent()) {
                IdempotencyEntry entry = existing.get();
                if (entry.isInProgress()) {
                    throw new RequestInProgressException("Request is currently in progress for idempotency key: " + idempotencyKey);
                }
                if (!entry.requestHash().equals(requestHash)) {
                    throw new IdempotencyConflictException("Payload does not match original request for key: " + idempotencyKey);
                }
            }
            idempotencyStorage.start(scopeKey, requestHash);
        }

        // New profile created with active: true; credentials set during activation flow (Spec 3.2)
        Instant now = Instant.now();
        String initialPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
        Profile newProfile = new Profile(
                UUID.randomUUID(),
                displayName.trim(),
                normalizedEmail,
                roles,
                restaurantId,
                driverId,
                initialPasswordHash,
                true,
                now,
                now
        );

        Profile saved = profileRepository.save(newProfile);

        if (idempotencyKey != null) {
            idempotencyStorage.complete(scopeKey, requestHash, 201, "{\"id\":\"" + saved.id() + "\"}");
        }

        return saved;
    }

    @Override
    @Transactional
    public Profile updateProfileStatus(UUID adminId, UUID profileId, boolean active, String idempotencyKey, String rawBody) {
        Objects.requireNonNull(adminId, "adminId cannot be null");
        Objects.requireNonNull(profileId, "profileId cannot be null");

        // Spec 3.2: An ADMIN cannot deactivate their own profile (409 INVALID_TRANSITION)
        if (adminId.equals(profileId) && !active) {
            throw new InvalidTransitionException("An ADMIN cannot deactivate their own profile");
        }

        String requestHash = computeSha256(rawBody != null ? rawBody : Boolean.toString(active));
        String scopeKey = adminId + ":PATCH:/api/v1/admin/profiles/" + profileId + ":" + (idempotencyKey != null ? idempotencyKey : UUID.randomUUID());

        if (idempotencyKey != null) {
            Optional<IdempotencyEntry> existing = idempotencyStorage.find(scopeKey);
            if (existing.isPresent()) {
                IdempotencyEntry entry = existing.get();
                if (entry.isInProgress()) {
                    throw new RequestInProgressException("Request is currently in progress for idempotency key: " + idempotencyKey);
                }
                if (!entry.requestHash().equals(requestHash)) {
                    throw new IdempotencyConflictException("Payload does not match original request for key: " + idempotencyKey);
                }
            }
            idempotencyStorage.start(scopeKey, requestHash);
        }

        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found: " + profileId));

        Profile updated = profile.withActive(active, Instant.now());
        Profile saved = profileRepository.save(updated);

        if (idempotencyKey != null) {
            idempotencyStorage.complete(scopeKey, requestHash, 200, "{\"id\":\"" + saved.id() + "\",\"active\":" + active + "}");
        }

        return saved;
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
