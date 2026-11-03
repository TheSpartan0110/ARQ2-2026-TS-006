package com.eatsnow.messaging.consumer;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.common.messaging.MessageEnvelope;
import com.eatsnow.messaging.publisher.MessagePublisher;
import com.eatsnow.messaging.support.LogContext;
import com.eatsnow.messaging.support.MessageJson;
import com.eatsnow.messaging.support.MessageTypes;
import com.eatsnow.messaging.support.PoisonMessageException;
import com.eatsnow.messaging.tracing.TraceSupport;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Idempotent consumer with the retry policy of Spec 4.3:
 * 1. parse and validate the envelope (malformed or unknown schema version: straight to the DLQ);
 * 2. in ONE database transaction: record (consumer, messageId) and run the business handler;
 * 3. the broker delivery is acknowledged only after that transaction commits (Spring acks when this method returns);
 * 4. transient failure: republish to the retry queue (5 s, 30 s, 120 s), then reject to the DLQ.
 */
public class EnvelopeConsumer {
    private static final Logger log = LoggerFactory.getLogger(EnvelopeConsumer.class);
    public static final String RETRY_HEADER = "x-eatsnow-retry-count";
    private static final int[] RETRY_DELAYS_SECONDS = {5, 30, 120};

    private final TransactionTemplate tx;
    private final ProcessedMessages processedMessages;
    private final MessagePublisher publisher;
    private final TraceSupport trace;
    private final MeterRegistry registry;

    public EnvelopeConsumer(TransactionTemplate tx, ProcessedMessages processedMessages, MessagePublisher publisher,
                            TraceSupport trace, MeterRegistry registry) {
        this.tx = tx;
        this.processedMessages = processedMessages;
        this.publisher = publisher;
        this.trace = trace;
        this.registry = registry;
    }

    public void consume(String consumerName, String queue, Message amqp, EnvelopeHandler handler) {
        MessageEnvelope<JsonNode> env = parse(queue, amqp);
        String operation = "consume " + env.messageType();

        Span span = trace.startSpan(env.traceparent(), operation, Span.Kind.CONSUMER);
        try (Tracer.SpanInScope ignored = trace.scope(span)) {
            LogContext.workflow(env.orderId(), env.correlationId());
            MDC.put(LogContext.MESSAGE_ID, env.messageId().toString());
            MDC.put(LogContext.MESSAGE_TYPE, env.messageType());
            if (env.causationId() != null) {
                MDC.put(LogContext.CAUSATION_ID, env.causationId().toString());
            }
            try {
                Boolean processed = tx.execute(status -> {
                    if (!processedMessages.markProcessed(consumerName, env.messageId())) {
                        return false;                       // duplicate: no business effect (Spec 4.3 item 2)
                    }
                    handler.handle(env);
                    return true;
                });
                String outcome = Boolean.TRUE.equals(processed) ? "SUCCESS" : "DUPLICATE";
                count(env.messageType(), outcome);
                log.info("Message handled", kv("operation", operation), kv("outcome", outcome));
            } catch (PoisonMessageException e) {
                span.error(e);
                count(env.messageType(), "FAILURE");
                log.error("Message quarantined in the DLQ: {}", e.getMessage(), kv("operation", operation),
                        kv("outcome", "FAILURE"), kv("errorCode", "POISON_MESSAGE"));
                throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
            } catch (RuntimeException e) {
                span.error(e);
                retryOrReject(queue, amqp, env, operation, e);
            }
        } finally {
            span.end();
            LogContext.clear();
        }
    }

    private MessageEnvelope<JsonNode> parse(String queue, Message amqp) {
        try {
            MessageEnvelope<JsonNode> env = MessageJson.readEnvelope(amqp.getBody());   // null fields -> exception
            if (env.schemaVersion() != 1) {
                throw new IllegalArgumentException("Unsupported schemaVersion " + env.schemaVersion());
            }
            return env;
        } catch (Exception e) {
            count("unknown", "FAILURE");
            log.error("Malformed message sent to the DLQ", kv("operation", "consume " + queue),
                    kv("outcome", "FAILURE"), kv("errorCode", "MALFORMED_MESSAGE"));
            throw new AmqpRejectAndDontRequeueException("Malformed message", e);
        }
    }

    private void retryOrReject(String queue, Message amqp, MessageEnvelope<JsonNode> env, String operation,
                               RuntimeException cause) {
        Object header = amqp.getMessageProperties().getHeader(RETRY_HEADER);
        int attempt = header instanceof Number n ? n.intValue() : 0;

        if (attempt < RETRY_DELAYS_SECONDS.length) {
            String delay = RETRY_DELAYS_SECONDS[attempt] + "s";
            publisher.publish(MessageTypes.RETRY, queue + ".retry." + delay, amqp.getBody(), env.messageId(),
                    Map.of(RETRY_HEADER, attempt + 1));
            count(env.messageType(), "RETRY");
            log.warn("Transient failure, retrying in {}", delay, kv("operation", operation), kv("outcome", "RETRY"),
                    kv("errorCode", cause.getClass().getSimpleName()), kv("attempt", attempt + 1));
            return;                                         // normal return: the original delivery is acknowledged
        }
        count(env.messageType(), "FAILURE");
        log.error("Retries exhausted, sending to the DLQ", kv("operation", operation), kv("outcome", "FAILURE"),
                kv("errorCode", cause.getClass().getSimpleName()), cause);
        throw new AmqpRejectAndDontRequeueException("Retries exhausted", cause);
    }

    private void count(String messageType, String outcome) {
        // Prometheus: eatsnow_messages_consumed_total{message_type, outcome}; `service` is added by the scrape.
        registry.counter("eatsnow.messages.consumed", "message_type", messageType, "outcome", outcome).increment();
    }
}
