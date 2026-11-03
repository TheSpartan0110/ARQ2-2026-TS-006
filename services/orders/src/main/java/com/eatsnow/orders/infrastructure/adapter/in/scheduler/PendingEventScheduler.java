package com.eatsnow.orders.infrastructure.adapter.in.scheduler;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.messaging.support.LogContext;
import com.eatsnow.orders.domain.port.in.HandleOrderEventUseCase;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-evaluates premature events every 10 seconds (Spec 4.3 item 3) in case no transition triggered them, and exposes
 * the age of the oldest one for the PrerequisiteWaitTooLong alert. State is never changed silently: an event is only
 * applied when its prerequisite is really met.
 */
@Component
public class PendingEventScheduler {
    private static final Logger log = LoggerFactory.getLogger(PendingEventScheduler.class);

    private final HandleOrderEventUseCase useCase;
    private final AtomicLong oldestPendingSeconds = new AtomicLong();

    public PendingEventScheduler(HandleOrderEventUseCase useCase, MeterRegistry registry) {
        this.useCase = useCase;
        // Prometheus: eatsnow_pending_messages_oldest_seconds (Spec 6.4)
        Gauge.builder("eatsnow.pending.messages.oldest.seconds", oldestPendingSeconds, AtomicLong::get).register(registry);
    }

    @Scheduled(fixedDelayString = "${eatsnow.orders.pending-reevaluation-ms:10000}")
    public void reevaluate() {
        try {
            for (UUID orderId : useCase.ordersWithPendingEvents(100)) {
                try {
                    LogContext.workflow(orderId, null);
                    useCase.reevaluatePending(orderId);       // each order in its own transaction
                } catch (RuntimeException e) {
                    log.warn("Re-evaluation of pending events failed", kv("operation", "reevaluate pending"),
                            kv("outcome", "RETRY"), e);
                } finally {
                    LogContext.clear();
                }
            }
            oldestPendingSeconds.set(useCase.oldestPendingSeconds());
        } catch (RuntimeException e) {
            log.warn("Pending event polling failed", kv("operation", "reevaluate pending"), kv("outcome", "FAILURE"), e);
        }
    }
}
