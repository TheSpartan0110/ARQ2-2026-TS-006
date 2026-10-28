package com.eatsnow.common.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Common messaging envelope for all EatsNow RabbitMQ events and commands (Spec 4.1).
 * Carries workflow correlationId, causationId, and traceparent for end-to-end observability.
 */
public record MessageEnvelope<T>(
        UUID messageId,
        String messageType,
        int schemaVersion,
        Instant occurredAt,
        String producer,
        UUID orderId,
        UUID correlationId,
        UUID causationId,
        UUID aggregateId,
        int aggregateVersion,
        String traceparent,
        T payload
) {
    public MessageEnvelope {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        Objects.requireNonNull(messageType, "messageType cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(producer, "producer cannot be null");
        Objects.requireNonNull(orderId, "orderId cannot be null");
        Objects.requireNonNull(correlationId, "correlationId cannot be null");
        Objects.requireNonNull(aggregateId, "aggregateId cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
    }

    public static <T> MessageEnvelope<T> create(
            String messageType,
            String producer,
            UUID orderId,
            UUID correlationId,
            UUID causationId,
            UUID aggregateId,
            int aggregateVersion,
            String traceparent,
            T payload
    ) {
        return new MessageEnvelope<>(
                UUID.randomUUID(),
                messageType,
                1,
                Instant.now(),
                producer,
                orderId,
                correlationId,
                causationId,
                aggregateId,
                aggregateVersion,
                traceparent,
                payload
        );
    }
}
