package com.eatsnow.messaging.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;

/**
 * W3C trace context through the broker (Spec 6.3): the outbox stores the traceparent of the transaction,
 * the relay publishes under a child span, and the consumer continues the same trace.
 */
public class TraceSupport {
    private static final String TRACEPARENT = "traceparent";

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
        return tracer.nextSpan().name(name).kind(kind).start();
    }

    /** Makes the span current (also fills traceId/spanId in the logs) until the returned scope is closed. */
    public Tracer.SpanInScope scope(Span span) {
        return tracer.withSpan(span);
    }
}
