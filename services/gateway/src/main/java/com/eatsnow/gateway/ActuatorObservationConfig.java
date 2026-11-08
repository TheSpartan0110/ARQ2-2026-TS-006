package com.eatsnow.gateway;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

/**
 * Keeps requests to /actuator (Prometheus scrapes, health checks) out of traces and HTTP server metrics. They belong to
 * no order workflow and would only add noise to Tempo searches and the service graph (Spec 6.3).
 */
@Configuration
public class ActuatorObservationConfig {

    @Bean
    public ObservationPredicate noActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext request
                && request.getCarrier().getPath().value().startsWith("/actuator"));
    }
}
