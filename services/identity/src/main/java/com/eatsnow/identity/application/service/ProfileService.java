package com.eatsnow.identity.application.service;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.IdempotencyConflictException;
import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.common.domain.exception.RequestInProgressException;
import com.eatsnow.common.domain.model.IdempotencyEntry;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.in.GetProfileUseCase;
import com.eatsnow.identity.domain.port.in.UpdateMyProfileUseCase;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service handling personal profile queries and updates (Spec 3.2).
 */
@Service
public class ProfileService implements GetProfileUseCase, UpdateMyProfileUseCase {

    private final ProfileRepositoryPort profileRepository;
    private final IdempotencyStoragePort idempotencyStorage;

    public ProfileService(ProfileRepositoryPort profileRepository, IdempotencyStoragePort idempotencyStorage) {
        this.profileRepository = profileRepository;
        this.idempotencyStorage = idempotencyStorage;
    }

    @Override
    @Transactional(readOnly = true)
    public Profile getProfileById(UUID id) {
        Objects.requireNonNull(id, "id cannot be null");
        return profileRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found: " + id));
    }

    @Override
    @Transactional
    public Profile updateDisplayName(UUID id, String displayName, String idempotencyKey, String rawBody) {
        Objects.requireNonNull(id, "id cannot be null");
        if (displayName == null || displayName.isBlank() || displayName.length() > 100) {
            throw new InvalidRequestException("displayName must be between 1 and 100 characters");
        }

        String requestHash = computeSha256(rawBody != null ? rawBody : displayName);
        String scopeKey = id + ":PATCH:/api/v1/profiles/me:" + (idempotencyKey != null ? idempotencyKey : UUID.randomUUID());

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

        Profile profile = profileRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found: " + id));

        Profile updated = profile.withDisplayName(displayName.trim(), Instant.now());
        Profile saved = profileRepository.save(updated);

        if (idempotencyKey != null) {
            idempotencyStorage.complete(scopeKey, requestHash, 200, "{\"id\":\"" + saved.id() + "\"}");
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
