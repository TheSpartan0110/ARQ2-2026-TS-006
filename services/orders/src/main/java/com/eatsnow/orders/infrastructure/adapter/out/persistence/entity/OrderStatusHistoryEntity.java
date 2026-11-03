package com.eatsnow.orders.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_status_history", schema = "orders")
public class OrderStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "from_payment_status", length = 30)
    private String fromPaymentStatus;

    @Column(name = "to_payment_status", nullable = false, length = 30)
    private String toPaymentStatus;

    @Column(name = "order_version", nullable = false)
    private int orderVersion;

    @Column(name = "caused_by")
    private UUID causedBy;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected OrderStatusHistoryEntity() {
    }

    public OrderStatusHistoryEntity(UUID orderId, String fromStatus, String toStatus, String fromPaymentStatus,
                                    String toPaymentStatus, int orderVersion, UUID causedBy, Instant occurredAt) {
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.fromPaymentStatus = fromPaymentStatus;
        this.toPaymentStatus = toPaymentStatus;
        this.orderVersion = orderVersion;
        this.causedBy = causedBy;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getFromPaymentStatus() {
        return fromPaymentStatus;
    }

    public void setFromPaymentStatus(String fromPaymentStatus) {
        this.fromPaymentStatus = fromPaymentStatus;
    }

    public String getToPaymentStatus() {
        return toPaymentStatus;
    }

    public void setToPaymentStatus(String toPaymentStatus) {
        this.toPaymentStatus = toPaymentStatus;
    }

    public int getOrderVersion() {
        return orderVersion;
    }

    public void setOrderVersion(int orderVersion) {
        this.orderVersion = orderVersion;
    }

    public UUID getCausedBy() {
        return causedBy;
    }

    public void setCausedBy(UUID causedBy) {
        this.causedBy = causedBy;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

}
