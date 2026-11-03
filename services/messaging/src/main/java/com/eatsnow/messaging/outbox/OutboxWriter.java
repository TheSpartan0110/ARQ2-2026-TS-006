package com.eatsnow.messaging.outbox;

import com.eatsnow.common.messaging.MessageEnvelope;
import com.eatsnow.messaging.support.MessageJson;
import com.eatsnow.messaging.tracing.TraceSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox, write side (ADR 001). It must run inside the same database transaction that changes the
 * business data: both are committed or neither is. The relay publishes the row afterwards.
 */
public class OutboxWriter {
    private final JdbcClient jdbc;
    private final TraceSupport trace;

    public OutboxWriter(JdbcClient jdbc, TraceSupport trace) {
        this.jdbc = jdbc;
        this.trace = trace;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(String exchange, String messageType, String producer, UUID orderId, UUID correlationId,
                        UUID causationId, UUID aggregateId, int aggregateVersion, Object payload) {
        String traceparent = trace.currentTraceparent();
        JsonNode payloadTree = MessageJson.mapper().valueToTree(payload);
        MessageEnvelope<JsonNode> envelope = MessageEnvelope.create(messageType, producer, orderId, correlationId,
                causationId, aggregateId, aggregateVersion, traceparent, payloadTree);

        jdbc.sql("""
                INSERT INTO outbox (message_id, exchange, routing_key, body, order_id, correlation_id, traceparent)
                VALUES (:messageId, :exchange, :routingKey, :body, :orderId, :correlationId, :traceparent)
                """)
                .param("messageId", envelope.messageId())
                .param("exchange", exchange)
                .param("routingKey", messageType)           // the routing key is the case-sensitive messageType
                .param("body", MessageJson.mapper().valueToTree(envelope).toString())
                .param("orderId", orderId)
                .param("correlationId", correlationId)
                .param("traceparent", traceparent)
                .update();
        return envelope.messageId();
    }
}
