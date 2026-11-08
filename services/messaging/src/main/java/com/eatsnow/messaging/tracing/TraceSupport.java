package com.eatsnow.messaging.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * W3C trace context through the broker (Spec 6.3): the outbox stores the traceparent of the transaction,
 * the relay publishes under a child span, and the consumer continues the same trace.
 */
public class TraceSupport {
    private static final String TRACEPARENT = "traceparent";

    /** Span attributes that link every trace of one order workflow (Spec 6.3); search them in Jaeger or Tempo. */
    public static final String ORDER_ID_ATTRIBUTE = "eatsnow.order_id";
    public static final String CORRELATION_ID_ATTRIBUTE = "eatsnow.correlation_id";

    private final Tracer tracer;
    private final Propagator propagator;

    public TraceSupport(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /** traceparent of the active span, or null when there is none. */
    public String currentTraceparent() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(span.context(), carrier, Map::put);
        return carrier.get(TRACEPARENT);
    }

    /** Starts a span that is a child of the given traceparent (or of the current span if it is null). */
    public Span startSpan(String traceparent, String name, Span.Kind kind) {
        if (traceparent != null && !traceparent.isBlank()) {
            Map<String, String> carrier = Map.of(TRACEPARENT, traceparent);
            return propagator.extract(carrier, Map::get).name(name).kind(kind).start();
        }
        // Span has no kind() in Micrometer Tracing 1.3: build the span to set its kind.
        Span.Builder builder = tracer.spanBuilder().name(name).kind(kind);
        Span current = tracer.currentSpan();
        if (current != null) {
            builder.setParent(current.context());
        }
        return builder.start();
    }

    /**
     * Tags a span with the order workflow. An order spans several traces (creation, restaurant decisions, driver
     * actions start their own); searching by the workflow correlation id finds all of them.
     */
    public static void tagWorkflow(Span span, UUID orderId, UUID correlationId) {
        if (span == null) {
            return;
        }
        if (orderId != null) {
            span.tag(ORDER_ID_ATTRIBUTE, orderId.toString());
        }
        if (correlationId != null) {
            span.tag(CORRELATION_ID_ATTRIBUTE, correlationId.toString());
        }
    }

    /** Tags the active span (for example the HTTP server span of a request that acts on an order). */
    public void tagCurrentWorkflow(UUID orderId, UUID correlationId) {
        tagWorkflow(tracer.currentSpan(), orderId, correlationId);
    }

    /** Makes the span current (also fills traceId/spanId in the logs) until the returned scope is closed. */
    public Tracer.SpanInScope scope(Span span) {
        return tracer.withSpan(span);
    }
}
