package com.eatsnow.messaging.tracing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Workflow trace context per order (Spec 6.3): the first traceparent this service received for the order. */
public class WorkflowTraces {
    private final JdbcClient jdbc;

    public WorkflowTraces(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Keeps the first context only: later messages of the same order belong to the same workflow. */
    public void record(UUID orderId, String traceparent) {
        if (orderId == null || traceparent == null || traceparent.isBlank()) {
            return;
        }
        jdbc.sql("INSERT INTO workflow_traces (order_id, traceparent) VALUES (:orderId, :traceparent) "
                        + "ON CONFLICT DO NOTHING")
                .param("orderId", orderId)
                .param("traceparent", traceparent)
                .update();
    }

    public Optional<String> find(UUID orderId) {
        return jdbc.sql("SELECT traceparent FROM workflow_traces WHERE order_id = :orderId")
                .param("orderId", orderId)
                .query(String.class)
                .optional();
    }
}
