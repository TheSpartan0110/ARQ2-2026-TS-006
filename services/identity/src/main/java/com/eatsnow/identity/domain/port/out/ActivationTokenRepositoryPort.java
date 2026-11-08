package com.eatsnow.identity.domain.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for one-time activation tokens. Only token hashes are stored (Spec 3.2).
 */
public interface ActivationTokenRepositoryPort {

    void save(String tokenHash, UUID profileId, Instant expiresAt);

    /** Profile of an unused, unexpired token. */
    Optional<UUID> findUsableProfileId(String tokenHash, Instant now);

    /** Marks the token used; returns false when it was already used or expired (concurrent activation). */
    boolean markUsed(String tokenHash, Instant now);
}
