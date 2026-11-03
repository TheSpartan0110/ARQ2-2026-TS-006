package com.eatsnow.messaging.consumer;

import com.eatsnow.common.messaging.MessageEnvelope;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Business reaction to one message. It runs inside the same database transaction that records the message as
 * processed, so the business effects and the deduplication record commit (or roll back) together.
 */
@FunctionalInterface
public interface EnvelopeHandler {
    void handle(MessageEnvelope<JsonNode> envelope);
}
