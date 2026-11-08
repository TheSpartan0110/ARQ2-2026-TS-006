package com.eatsnow.common.web;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Keeps observations that belong to no order workflow out of the traces, so Tempo and Jaeger searches and the
 * service graph show only real work (Spec 6.3).
 */
@Configuration
public class ActuatorObservationConfig {

    /** Spring's observation name for every @Scheduled run. */
    static final String SCHEDULED_TASK_OBSERVATION = "tasks.scheduled.execution";

    /** Requests to /actuator (Prometheus scrapes, health checks). Also removed from HTTP server metrics. */
    @Bean
    public ObservationPredicate noActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext request
                && request.getCarrier().getRequestURI().startsWith("/actuator"));
    }

    /**
     * Pollers (outbox relay, pending-event reevaluation, payment reconciliation, delivery assignment, email dispatch)
     * run every second or so; a trace per run buried the order traces. The work they do on an order still produces
     * spans: the relay's publish span continues the trace stored with the outbox entry.
     */
    @Bean
    public ObservationPredicate noScheduledTaskObservations() {
        return (name, context) -> !SCHEDULED_TASK_OBSERVATION.equals(name);
    }
}
