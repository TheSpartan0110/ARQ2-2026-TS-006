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
import com.eatsnow.identity.domain.port.out.ActivationNotifierPort;
import com.eatsnow.identity.domain.port.out.ActivationTokenRepositoryPort;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import com.eatsnow.identity.domain.port.out.TokenRevocationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service managing administrative operations on user profiles (Spec 3.2).
 */
@Service
public class AdminProfileService implements AdminProfileUseCase {

    static final Duration ACTIVATION_LINK_VALIDITY = Duration.ofHours(72);

    private final ProfileRepositoryPort profileRepository;
    private final IdempotencyStoragePort idempotencyStorage;
    private final ActivationTokenRepositoryPort activationTokens;
    private final ActivationNotifierPort activationNotifier;
    private final TokenRevocationPort tokenRevocation;

    public AdminProfileService(
            ProfileRepositoryPort profileRepository,
            IdempotencyStoragePort idempotencyStorage,
            ActivationTokenRepositoryPort activationTokens,
            ActivationNotifierPort activationNotifier,
            TokenRevocationPort tokenRevocation
    ) {
        this.profileRepository = profileRepository;
        this.idempotencyStorage = idempotencyStorage;
        this.activationTokens = activationTokens;
        this.activationNotifier = activationNotifier;
        this.tokenRevocation = tokenRevocation;
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

        String name = ProfileRules.validDisplayName(displayName);
        String normalizedEmail = ProfileRules.normalizeEmail(email);

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

        // Spec 3.2: no password travels through this API. The profile has none until the person follows the
        // one-time activation link and sets it; until then it cannot log in.
        Instant now = Instant.now();
        Profile newProfile = new Profile(
                UUID.randomUUID(),
                name,
                normalizedEmail,
                roles,
                restaurantId,
                driverId,
                null,
                true,
                now,
                now
        );

        Profile saved = profileRepository.save(newProfile);

        String activationToken = ProfileRules.newActivationToken();
        activationTokens.save(ProfileRules.sha256(activationToken), saved.id(), now.plus(ACTIVATION_LINK_VALIDITY));
        activationNotifier.sendActivationLink(saved, activationToken);

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

        // Spec 3.2: deactivation revokes refresh tokens at once; issued access tokens expire within 15 minutes.
        if (!active) {
            tokenRevocation.revokeAll(saved.email());
        }

        if (idempotencyKey != null) {
            idempotencyStorage.complete(scopeKey, requestHash, 200, "{\"id\":\"" + saved.id() + "\",\"active\":" + active + "}");
        }

        return saved;
    }

    private String computeSha256(String input) {
        return ProfileRules.sha256(input);
    }
}
