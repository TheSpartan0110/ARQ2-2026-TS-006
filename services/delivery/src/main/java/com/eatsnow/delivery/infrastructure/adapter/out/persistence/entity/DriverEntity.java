package com.eatsnow.delivery.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "drivers", schema = "deliveries")
public class DriverEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "on_duty", nullable = false)
    private boolean onDuty;

    @Column(name = "active_delivery_id")
    private UUID activeDeliveryId;

    @Column(name = "last_assigned_at")
    private Instant lastAssignedAt;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DriverEntity() {
    }

    public DriverEntity(UUID id, UUID profileId, boolean onDuty, UUID activeDeliveryId, Instant lastAssignedAt, int version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.profileId = profileId;
        this.onDuty = onDuty;
        this.activeDeliveryId = activeDeliveryId;
        this.lastAssignedAt = lastAssignedAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public void setProfileId(UUID profileId) {
        this.profileId = profileId;
    }

    public boolean getOnDuty() {
        return onDuty;
    }

    public void setOnDuty(boolean onDuty) {
        this.onDuty = onDuty;
    }

    public UUID getActiveDeliveryId() {
        return activeDeliveryId;
    }

    public void setActiveDeliveryId(UUID activeDeliveryId) {
        this.activeDeliveryId = activeDeliveryId;
    }

    public Instant getLastAssignedAt() {
        return lastAssignedAt;
    }

    public void setLastAssignedAt(Instant lastAssignedAt) {
        this.lastAssignedAt = lastAssignedAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

}
