package com.eatsnow.delivery.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.delivery.domain.model.Delivery;
import com.eatsnow.delivery.domain.model.DeliveryStatus;
import com.eatsnow.delivery.domain.model.Driver;
import com.eatsnow.delivery.domain.port.out.DeliveryEventPublisherPort;
import com.eatsnow.delivery.domain.port.out.DeliveryRepositoryPort;
import com.eatsnow.delivery.domain.port.out.DriverRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assigns ONE delivery in its own transaction (Spec 3.6): the driver claim (a conditional update), the Delivery change
 * to ASSIGNED and the DeliveryAssigned outbox entry commit together or not at all. When two deliveries race for the same
 * driver only one claim succeeds; the other keeps looking at the next candidate and otherwise stays UNASSIGNED.
 */
@Component
public class DeliveryAssigner {
    private static final Logger log = LoggerFactory.getLogger(DeliveryAssigner.class);
    private static final int CANDIDATES = 5;

    private final DeliveryRepositoryPort deliveries;
    private final DriverRepositoryPort drivers;
    private final DeliveryEventPublisherPort events;

    public DeliveryAssigner(DeliveryRepositoryPort deliveries, DriverRepositoryPort drivers,
                            DeliveryEventPublisherPort events) {
        this.deliveries = deliveries;
        this.drivers = drivers;
        this.events = events;
    }

    /** @return true when the delivery was assigned, false when it is no longer UNASSIGNED or no driver is assignable */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean assignOne(UUID deliveryId) {
        Optional<Delivery> locked = deliveries.findByIdForUpdate(deliveryId);
        if (locked.isEmpty() || locked.get().status() != DeliveryStatus.UNASSIGNED) {
            return false;
        }
        Delivery delivery = locked.get();
        for (Driver driver : drivers.findAssignable(CANDIDATES)) {
            if (!drivers.claim(driver.id(), driver.version(), deliveryId)) {
                continue;                                  // another delivery won this driver: try the next one
            }
            Delivery assigned = delivery.assignTo(driver.id(), Instant.now());
            if (!deliveries.updateIfVersion(assigned, delivery.version())) {
                throw new IllegalStateException("Delivery changed while its row was locked");   // rolls the claim back
            }
            events.assigned(assigned);
            log.info("Delivery assigned", kv("operation", "assign delivery"), kv("outcome", "SUCCESS"),
                    kv("orderId", assigned.orderId()), kv("correlationId", assigned.correlationId()),
                    kv("deliveryStatus", assigned.status()));
            return true;
        }
        return false;                                      // stays UNASSIGNED; the order stays PREPARING
    }
}
