package com.eatsnow.delivery.infrastructure.adapter.in.scheduler;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.delivery.domain.port.in.AssignDeliveriesUseCase;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries the assignment of UNASSIGNED deliveries every 10 seconds (Spec 3.6) and exposes the age of the oldest one for
 * the DeliveryUnassigned alert. A delivery is never cancelled automatically.
 */
@Component
public class AssignmentScheduler {
    private static final Logger log = LoggerFactory.getLogger(AssignmentScheduler.class);

    private final AssignDeliveriesUseCase assignment;
    private final AtomicLong oldestUnassignedSeconds = new AtomicLong();

    public AssignmentScheduler(AssignDeliveriesUseCase assignment, MeterRegistry registry) {
        this.assignment = assignment;
        // Prometheus: eatsnow_deliveries_unassigned_oldest_seconds (Spec 6.4)
        Gauge.builder("eatsnow.deliveries.unassigned.oldest.seconds", oldestUnassignedSeconds, AtomicLong::get)
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${eatsnow.delivery.assignment-interval-ms:10000}")
    public void retryAssignments() {
        try {
            assignment.assignPending();
            oldestUnassignedSeconds.set(assignment.oldestUnassignedSeconds());
        } catch (RuntimeException e) {
            log.warn("Assignment retry failed", kv("operation", "assign delivery"), kv("outcome", "FAILURE"), e);
        }
    }
}
