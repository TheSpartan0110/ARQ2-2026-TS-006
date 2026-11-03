package com.eatsnow.messaging.consumer;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Idempotent consumer table: one row per (consumer, messageId) (Spec 4.3 item 2). */
public class ProcessedMessages {
    private final JdbcClient jdbc;

    public ProcessedMessages(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true the first time a message is seen, false for a duplicate. */
    public boolean markProcessed(String consumer, UUID messageId) {
        return jdbc.sql("""
                INSERT INTO processed_messages (consumer, message_id) VALUES (:consumer, :messageId)
                ON CONFLICT DO NOTHING
                """)
                .param("consumer", consumer)
                .param("messageId", messageId)
                .update() == 1;
    }
}
