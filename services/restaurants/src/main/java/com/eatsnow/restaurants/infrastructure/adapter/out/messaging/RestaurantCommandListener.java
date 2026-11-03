package com.eatsnow.restaurants.infrastructure.adapter.out.messaging;

import com.eatsnow.messaging.consumer.EnvelopeConsumer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Entry point of the `restaurants.commands` queue (Spec 4.2.1). */
@Component
public class RestaurantCommandListener {
    static final String QUEUE = "restaurants.commands";

    private final EnvelopeConsumer consumer;
    private final RestaurantCommandHandler handler;

    public RestaurantCommandListener(EnvelopeConsumer consumer, RestaurantCommandHandler handler) {
        this.consumer = consumer;
        this.handler = handler;
    }

    @RabbitListener(queues = QUEUE)
    public void onMessage(Message message) {
        consumer.consume("restaurants", QUEUE, message, handler);
    }
}
