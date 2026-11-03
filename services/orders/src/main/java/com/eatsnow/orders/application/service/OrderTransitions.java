package com.eatsnow.orders.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.orders.domain.model.Order;
import com.eatsnow.orders.domain.model.OrderStateChange;
import com.eatsnow.orders.domain.model.OrderStatus;
import com.eatsnow.orders.domain.model.PaymentStatus;
import com.eatsnow.orders.domain.port.out.OrderEventPublisherPort;
import com.eatsnow.orders.domain.port.out.OrderRepositoryPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place that changes `status` or `paymentStatus`. Every change is committed together with its history
 * row and an OrderStatusChanged outbox entry (Spec 2.4), inside the caller's transaction.
 */
@Component
public class OrderTransitions {
    private static final Logger log = LoggerFactory.getLogger(OrderTransitions.class);

    private final OrderRepositoryPort orders;
    private final OrderEventPublisherPort events;
    private final MeterRegistry registry;

    public OrderTransitions(OrderRepositoryPort orders, OrderEventPublisherPort events, MeterRegistry registry) {
        this.orders = orders;
        this.events = events;
        this.registry = registry;
    }

    /** Initial creation: there is no previous status. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCreation(Order created) {
        orders.appendHistory(new OrderStateChange(created.id(), null, created.status(), null,
                created.paymentStatus(), created.version(), null));
        events.orderStatusChanged(created, null, null);
    }

    /**
     * DeliveryAssigned keeps the order PREPARING (Spec 2.4): only the assignment reference and the version change, so no
     * OrderStatusChanged is emitted.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Order recordAssignment(Order current, UUID deliveryId, UUID driverId) {
        Order updated = current.withDelivery(deliveryId, driverId, Instant.now());
        orders.update(updated);
        log.info("Delivery assignment recorded", kv("operation", "order assignment"), kv("outcome", "SUCCESS"),
                kv("orderStatus", updated.status()), kv("orderVersion", updated.version()));
        return updated;
    }

    /** @param current the row as loaded with a lock; its version is the one being replaced */
    @Transactional(propagation = Propagation.MANDATORY)
    public Order apply(Order current, OrderStatus newStatus, PaymentStatus newPaymentStatus, UUID causedBy) {
        Order updated = current.withStates(newStatus, newPaymentStatus, Instant.now());
        orders.update(updated);
        orders.appendHistory(new OrderStateChange(current.id(), current.status(), newStatus,
                current.paymentStatus(), newPaymentStatus, updated.version(), causedBy));
        events.orderStatusChanged(updated, current.status(), causedBy);

        if (current.status() != newStatus) {
            registry.counter("eatsnow.order.transitions", "from", current.status().name(), "to", newStatus.name())
                    .increment();
        }
        log.info("Order state changed", kv("operation", "order transition"), kv("outcome", "SUCCESS"),
                kv("orderStatus", newStatus), kv("paymentStatus", newPaymentStatus),
                kv("orderVersion", updated.version()));
        return updated;
    }
}
