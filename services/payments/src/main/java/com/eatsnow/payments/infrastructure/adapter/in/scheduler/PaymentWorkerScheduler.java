package com.eatsnow.payments.infrastructure.adapter.in.scheduler;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.messaging.support.LogContext;
import com.eatsnow.messaging.tracing.TraceSupport;
import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.Refund;
import com.eatsnow.payments.domain.port.in.PaymentProcessingUseCase;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the payment use cases on a timer. Each payment is handled under a span that continues the trace of the
 * ChargePayment command (Spec 6.3), with the workflow fields in the logs.
 */
@Component
public class PaymentWorkerScheduler {
    private static final Logger log = LoggerFactory.getLogger(PaymentWorkerScheduler.class);

    private final PaymentProcessingUseCase processing;
    private final TraceSupport trace;
    private final AtomicLong oldestUnresolvedSeconds = new AtomicLong();
    private final AtomicLong oldestUnresolvedRefundSeconds = new AtomicLong();

    public PaymentWorkerScheduler(PaymentProcessingUseCase processing, TraceSupport trace, MeterRegistry registry) {
        this.processing = processing;
        this.trace = trace;
        // Prometheus: eatsnow_payment_unresolved_oldest_seconds{operation="CHARGE"} (Spec 6.4)
        Gauge.builder("eatsnow.payment.unresolved.oldest.seconds", oldestUnresolvedSeconds, AtomicLong::get)
                .tag("operation", "CHARGE").register(registry);
        Gauge.builder("eatsnow.payment.unresolved.oldest.seconds", oldestUnresolvedRefundSeconds, AtomicLong::get)
                .tag("operation", "REFUND").register(registry);
    }

    @Scheduled(fixedDelayString = "${eatsnow.payments.poll-ms:500}")
    public void chargePending() {
        try {
            for (Payment payment : processing.pendingCharges(20)) {
                handle(payment.traceparent(), payment.orderId(), payment.correlationId(), "charge payment",
                        () -> processing.charge(payment));
            }
            for (Refund refund : processing.pendingRefunds(20)) {
                handle(refund.traceparent(), refund.orderId(), refund.correlationId(), "refund payment",
                        () -> processing.refund(refund));
            }
            refreshGauge();
        } catch (RuntimeException e) {
            log.warn("Payment polling failed", kv("operation", "process pending payments"),
                    kv("outcome", "FAILURE"), e);
        }
    }

    @Scheduled(fixedDelayString = "${eatsnow.payments.reconcile-ms:30000}")
    public void reconcileUnknown() {
        try {
            for (Payment payment : processing.unresolvedPayments(50)) {
                handle(payment.traceparent(), payment.orderId(), payment.correlationId(), "reconcile payment",
                        () -> processing.reconcile(payment));
            }
            for (Refund refund : processing.unresolvedRefunds(50)) {
                handle(refund.traceparent(), refund.orderId(), refund.correlationId(), "reconcile refund",
                        () -> processing.reconcileRefund(refund));
            }
            refreshGauge();
        } catch (RuntimeException e) {
            log.warn("Reconciliation polling failed", kv("operation", "reconcile payments"),
                    kv("outcome", "FAILURE"), e);
        }
    }

    private void handle(String traceparent, UUID orderId, UUID correlationId, String operation, Runnable action) {
        Span span = trace.startSpan(traceparent, operation, Span.Kind.CLIENT);
        try (Tracer.SpanInScope ignored = trace.scope(span)) {
            LogContext.workflow(orderId, correlationId);
            action.run();
        } catch (RuntimeException e) {
            span.error(e);   // the payment keeps its status and is retried on the next cycle with the same key
            log.warn("Payment operation failed, will retry", kv("operation", operation), kv("outcome", "RETRY"), e);
        } finally {
            span.end();
            LogContext.clear();
        }
    }

    private void refreshGauge() {
        oldestUnresolvedSeconds.set(processing.oldestUnresolvedSeconds());
        oldestUnresolvedRefundSeconds.set(processing.oldestUnresolvedRefundSeconds());
    }
}
