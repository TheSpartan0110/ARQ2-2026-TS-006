package com.eatsnow.messaging.relay;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.messaging.publisher.MessagePublisher;
import com.eatsnow.messaging.support.LogContext;
import com.eatsnow.messaging.support.MessageJson;
import com.eatsnow.messaging.tracing.TraceSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional outbox, publish side. Polls unpublished rows, publishes them with broker confirms and marks them
 * published. Delivery is at-least-once: a crash between publish and commit republishes the message, and consumers
 * deduplicate by messageId (Spec 4.3 items 1-2).
 */
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;
    private static final int[] BACKOFF_SECONDS = {1, 5, 30, 60};   // Spec 4.3 item 1

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final MessagePublisher publisher;
    private final TraceSupport trace;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong oldestPendingSeconds = new AtomicLong();

    public OutboxRelay(JdbcClient jdbc, TransactionTemplate tx, MessagePublisher publisher, TraceSupport trace,
                       MeterRegistry registry) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.publisher = publisher;
        this.trace = trace;
        // Prometheus: eatsnow_outbox_pending and eatsnow_outbox_oldest_pending_seconds (Spec 6.4)
        Gauge.builder("eatsnow.outbox.pending", pending, AtomicLong::get).register(registry);
        Gauge.builder("eatsnow.outbox.oldest.pending.seconds", oldestPendingSeconds, AtomicLong::get).register(registry);
    }

    @Scheduled(fixedDelayString = "${eatsnow.outbox.poll-ms:500}")
    public void poll() {
        try {
            tx.executeWithoutResult(status -> drain());
        } catch (RuntimeException e) {
            log.warn("Outbox poll failed", kv("operation", "outbox poll"), kv("outcome", "FAILURE"), e);
        }
        refreshGauges();
    }

    private void drain() {
        // SKIP LOCKED lets several replicas share the outbox without publishing the same row at once.
        List<Row> rows = jdbc.sql("""
                SELECT id, message_id, exchange, routing_key, body, order_id, correlation_id, traceparent, attempts
                FROM outbox
                WHERE published_at IS NULL AND next_attempt_at <= now()
                ORDER BY created_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """)
                .param("limit", BATCH_SIZE)
                .query((rs, i) -> new Row(rs.getLong("id"), rs.getObject("message_id", UUID.class),
                        rs.getString("exchange"), rs.getString("routing_key"), rs.getString("body"),
                        rs.getObject("order_id", UUID.class), rs.getObject("correlation_id", UUID.class),
                        rs.getString("traceparent"), rs.getInt("attempts")))
                .list();

        for (Row row : rows) {
            try {
                publishOne(row);
                jdbc.sql("UPDATE outbox SET published_at = now() WHERE id = :id").param("id", row.id()).update();
            } catch (Exception e) {
                int attempts = row.attempts() + 1;
                int delay = BACKOFF_SECONDS[Math.min(attempts - 1, BACKOFF_SECONDS.length - 1)];
                jdbc.sql("""
                        UPDATE outbox
                        SET attempts = :attempts,
                            next_attempt_at = now() + CAST(:delay AS integer) * interval '1 second',
                            last_error = :error
                        WHERE id = :id
                        """)
                        .param("attempts", attempts)
                        .param("delay", delay)
                        .param("error", String.valueOf(e.getMessage()))
                        .param("id", row.id())
                        .update();
                LogContext.workflow(row.orderId(), row.correlationId());
                log.warn("Outbox publication failed, will retry", kv("operation", "publish " + row.routingKey()),
                        kv("outcome", "RETRY"), kv("messageId", row.messageId()), kv("retryInSeconds", delay));
                LogContext.clear();
                break;   // the broker is probably unavailable: stop hammering it in this cycle
            }
        }
    }

    private void publishOne(Row row) throws Exception {
        String operation = "publish " + row.routingKey();
        Span span = trace.startSpan(row.traceparent(), operation, Span.Kind.PRODUCER);
        try (Tracer.SpanInScope ignored = trace.scope(span)) {
            LogContext.workflow(row.orderId(), row.correlationId());
            // The envelope carries the publish span, so the consumer's span is its child (Spec 6.3).
            ObjectNode envelope = (ObjectNode) MessageJson.mapper().readTree(row.body());
            String traceparent = trace.currentTraceparent();
            if (traceparent != null) {
                envelope.put("traceparent", traceparent);
            }
            publisher.publish(row.exchange(), row.routingKey(), MessageJson.mapper().writeValueAsBytes(envelope),
                    row.messageId(), Map.of());
            log.info("Message published", kv("operation", operation), kv("outcome", "SUCCESS"),
                    kv("messageId", row.messageId()), kv("messageType", row.routingKey()));
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
            LogContext.clear();
        }
    }

    private void refreshGauges() {
        try {
            long[] stats = jdbc.sql("""
                    SELECT count(*) AS n,
                           COALESCE(EXTRACT(EPOCH FROM (now() - min(created_at))), 0) AS age
                    FROM outbox WHERE published_at IS NULL
                    """)
                    .query((rs, i) -> new long[] {rs.getLong("n"), (long) rs.getDouble("age")})
                    .single();
            pending.set(stats[0]);
            oldestPendingSeconds.set(stats[1]);
        } catch (RuntimeException e) {
            log.debug("Could not refresh outbox gauges", e);
        }
    }

    private record Row(long id, UUID messageId, String exchange, String routingKey, String body, UUID orderId,
                       UUID correlationId, String traceparent, int attempts) { }
}
