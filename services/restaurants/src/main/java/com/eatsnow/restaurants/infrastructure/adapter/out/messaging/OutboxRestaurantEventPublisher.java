package com.eatsnow.restaurants.infrastructure.adapter.out.messaging;

import com.eatsnow.messaging.contract.Payloads;
import com.eatsnow.messaging.outbox.OutboxWriter;
import com.eatsnow.messaging.support.MessageTypes;
import com.eatsnow.restaurants.domain.model.RestaurantOrder;
import com.eatsnow.restaurants.domain.port.out.RestaurantEventPublisherPort;
import org.springframework.stereotype.Component;

/** Writes the decision events to the transactional outbox (ADR 001); the relay publishes them after the commit. */
@Component
public class OutboxRestaurantEventPublisher implements RestaurantEventPublisherPort {
    private static final String PRODUCER = "restaurants";

    private final OutboxWriter outbox;

    public OutboxRestaurantEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void accepted(RestaurantOrder order) {
        publish(order, MessageTypes.RESTAURANT_ACCEPTED, new Payloads.RestaurantDecision(order.restaurantId()));
    }

    @Override
    public void rejected(RestaurantOrder order) {
        publish(order, MessageTypes.RESTAURANT_REJECTED,
                new Payloads.RestaurantRejected(order.restaurantId(), order.reason()));
    }

    @Override
    public void preparing(RestaurantOrder order) {
        publish(order, MessageTypes.RESTAURANT_PREPARING, new Payloads.RestaurantDecision(order.restaurantId()));
    }

    /** Stored workflow correlationId, causationId null (HTTP-triggered), aggregate = orderId (Spec 3.5). */
    private void publish(RestaurantOrder order, String messageType, Object payload) {
        outbox.enqueue(MessageTypes.EVENTS, messageType, PRODUCER, order.orderId(), order.correlationId(), null,
                order.orderId(), order.version(), payload);
    }
}
