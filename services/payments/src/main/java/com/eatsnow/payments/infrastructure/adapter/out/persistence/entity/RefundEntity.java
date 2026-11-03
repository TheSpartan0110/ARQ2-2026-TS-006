package com.eatsnow.payments.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds", schema = "payments")
public class RefundEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private UUID paymentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "provider_idempotency_key", nullable = false, length = 100)
    private String providerIdempotencyKey;

    @Column(name = "charge_idempotency_key", nullable = false, length = 100)
    private String chargeIdempotencyKey;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "command_message_id", nullable = false)
    private UUID commandMessageId;

    @Column(name = "traceparent", length = 100)
    private String traceparent;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RefundEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public String getProviderIdempotencyKey() {
        return providerIdempotencyKey;
    }

    public String getChargeIdempotencyKey() {
        return chargeIdempotencyKey;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public UUID getCommandMessageId() {
        return commandMessageId;
    }

    public String getTraceparent() {
        return traceparent;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}
