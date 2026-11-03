package com.eatsnow.delivery.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.delivery.domain.event.AssignmentTrigger;
import com.eatsnow.delivery.domain.model.Delivery;
import com.eatsnow.delivery.domain.port.in.RegisterDeliveryUseCase;
import com.eatsnow.delivery.domain.port.out.DeliveryRepositoryPort;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the UNASSIGNED delivery when AssignDelivery is consumed (Spec 3.6). Unique per orderId: a duplicate command
 * with a new messageId changes nothing. After the commit an assignment pass is triggered; waiting for a driver is
 * normal pending business work, not a transport failure (Spec 4.2.2).
 */
@Service
public class DeliveryRegistrationService implements RegisterDeliveryUseCase {
    private static final Logger log = LoggerFactory.getLogger(DeliveryRegistrationService.class);

    private final DeliveryRepositoryPort deliveries;
    private final ApplicationEventPublisher publisher;

    public DeliveryRegistrationService(DeliveryRepositoryPort deliveries, ApplicationEventPublisher publisher) {
        this.deliveries = deliveries;
        this.publisher = publisher;
    }

    @Override
    @Transactional
    public boolean register(Command command) {
        Delivery delivery = Delivery.unassigned(UUID.randomUUID(), command.orderId(), command.restaurantId(),
                command.deliveryAddress(), command.correlationId(), command.commandMessageId(), Instant.now());
        if (!deliveries.insertIfAbsent(delivery)) {
            log.info("Delivery already exists for the order", kv("operation", "register delivery"),
                    kv("outcome", "DUPLICATE"));
            return false;
        }
        publisher.publishEvent(new AssignmentTrigger("delivery registered"));     // handled after the commit
        return true;
    }
}
