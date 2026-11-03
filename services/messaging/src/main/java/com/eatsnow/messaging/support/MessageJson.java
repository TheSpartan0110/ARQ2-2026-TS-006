package com.eatsnow.messaging.support;

import com.eatsnow.common.messaging.MessageEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Dedicated mapper for messages. It ignores unknown fields on purpose: consumers must tolerate additive optional
 * fields (Spec 4.3 item 7), even in services whose HTTP layer rejects unknown request fields.
 */
public final class MessageJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private MessageJson() { }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static MessageEnvelope<JsonNode> readEnvelope(byte[] body) throws java.io.IOException {
        return MAPPER.readValue(body, new TypeReference<MessageEnvelope<JsonNode>>() { });
    }

    public static <T> T readPayload(MessageEnvelope<JsonNode> envelope, Class<T> type) {
        try {
            return MAPPER.treeToValue(envelope.payload(), type);
        } catch (JsonProcessingException e) {
            throw new PoisonMessageException("Invalid payload for " + envelope.messageType(), e);
        }
    }
}
