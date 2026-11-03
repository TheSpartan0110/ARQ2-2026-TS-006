package com.eatsnow.payments.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", schema = "payments")
public class PaymentEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "total_amount_minor", nullable = false)
    private long totalAmountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "provider_idempotency_key", nullable = false, length = 100)
    private String providerIdempotencyKey;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "encrypted_payment_token", columnDefinition = "TEXT")
    private String encryptedPaymentToken;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "command_message_id", nullable = false)
    private UUID commandMessageId;

    @Column(name = "traceparent", length = 100)
    private String traceparent;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "pending_since")
    private Instant pendingSince;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentEntity() {
    }

    public PaymentEntity(UUID id, UUID orderId, long totalAmountMinor, String currency, String status, String providerIdempotencyKey, String providerReference, String failureCode, String encryptedPaymentToken, UUID correlationId, UUID commandMessageId, String traceparent, int version, Instant pendingSince, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.totalAmountMinor = totalAmountMinor;
        this.currency = currency;
        this.status = status;
        this.providerIdempotencyKey = providerIdempotencyKey;
        this.providerReference = providerReference;
        this.failureCode = failureCode;
        this.encryptedPaymentToken = encryptedPaymentToken;
        this.correlationId = correlationId;
        this.commandMessageId = commandMessageId;
        this.traceparent = traceparent;
        this.version = version;
        this.pendingSince = pendingSince;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public long getTotalAmountMinor() {
        return totalAmountMinor;
    }

    public void setTotalAmountMinor(long totalAmountMinor) {
        this.totalAmountMinor = totalAmountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProviderIdempotencyKey() {
        return providerIdempotencyKey;
    }

    public void setProviderIdempotencyKey(String providerIdempotencyKey) {
        this.providerIdempotencyKey = providerIdempotencyKey;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getEncryptedPaymentToken() {
        return encryptedPaymentToken;
    }

    public void setEncryptedPaymentToken(String encryptedPaymentToken) {
        this.encryptedPaymentToken = encryptedPaymentToken;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public UUID getCommandMessageId() {
        return commandMessageId;
    }

    public void setCommandMessageId(UUID commandMessageId) {
        this.commandMessageId = commandMessageId;
    }

    public String getTraceparent() {
        return traceparent;
    }

    public void setTraceparent(String traceparent) {
        this.traceparent = traceparent;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getPendingSince() {
        return pendingSince;
    }

    public void setPendingSince(Instant pendingSince) {
        this.pendingSince = pendingSince;
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
