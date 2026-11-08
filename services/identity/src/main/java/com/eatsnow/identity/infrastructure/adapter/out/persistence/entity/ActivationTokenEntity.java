package com.eatsnow.identity.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activation_tokens", schema = "identity")
public class ActivationTokenEntity {

    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ActivationTokenEntity() {
    }

    public ActivationTokenEntity(String tokenHash, UUID profileId, Instant expiresAt, Instant createdAt) {
        this.tokenHash = tokenHash;
        this.profileId = profileId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
