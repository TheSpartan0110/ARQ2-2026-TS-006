package com.eatsnow.notifications.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Notification (Spec 2.2). The recorded attempt exists before any email provider call (ADR 006). */
public record Notification(UUID id, UUID orderId, UUID customerId, NotificationTrigger trigger,
                           NotificationStatus status, int attempts, Instant nextAttemptAt, UUID correlationId,
                           int version) {
}
