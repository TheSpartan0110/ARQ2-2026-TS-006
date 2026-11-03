package com.eatsnow.orders.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_secrets", schema = "orders")
public class OrderSecretEntity {

    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "encrypted_payment_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedPaymentToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderSecretEntity() {
    }

    public OrderSecretEntity(UUID orderId, String encryptedPaymentToken, Instant createdAt) {
        this.orderId = orderId;
        this.encryptedPaymentToken = encryptedPaymentToken;
        this.createdAt = createdAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getEncryptedPaymentToken() {
        return encryptedPaymentToken;
    }

    public void setEncryptedPaymentToken(String encryptedPaymentToken) {
        this.encryptedPaymentToken = encryptedPaymentToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

}
