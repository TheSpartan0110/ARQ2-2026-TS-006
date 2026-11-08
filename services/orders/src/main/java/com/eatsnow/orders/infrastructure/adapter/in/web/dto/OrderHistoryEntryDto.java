package com.eatsnow.orders.infrastructure.adapter.in.web.dto;

import com.eatsnow.orders.domain.model.OrderHistoryEntry;
import java.time.Instant;
import java.util.UUID;

/** OrderHistoryEntry schema of Spec 3.4: one status or payment-status change, oldest first. */
public record OrderHistoryEntryDto(String previousStatus, String status, String previousPaymentStatus,
                                   String paymentStatus, int version, UUID causedBy, Instant occurredAt) {

    public static OrderHistoryEntryDto fromDomain(OrderHistoryEntry e) {
        return new OrderHistoryEntryDto(
                e.fromStatus() != null ? e.fromStatus().name() : null, e.toStatus().name(),
                e.fromPaymentStatus() != null ? e.fromPaymentStatus().name() : null, e.toPaymentStatus().name(),
                e.orderVersion(), e.causedBy(), e.occurredAt());
    }
}
