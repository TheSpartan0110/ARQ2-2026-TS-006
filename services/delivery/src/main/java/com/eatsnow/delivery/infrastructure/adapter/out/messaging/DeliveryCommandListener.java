package com.eatsnow.delivery.infrastructure.adapter.out.messaging;

import com.eatsnow.messaging.consumer.EnvelopeConsumer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Entry point of the `delivery.commands` queue (Spec 4.2.1). Only Delivery is bound to the address-bearing queue. */
@Component
public class DeliveryCommandListener {
    static final String QUEUE = "delivery.commands";

    private final EnvelopeConsumer consumer;
    private final DeliveryCommandHandler handler;

    public DeliveryCommandListener(EnvelopeConsumer consumer, DeliveryCommandHandler handler) {
        this.consumer = consumer;
        this.handler = handler;
    }

    @RabbitListener(queues = QUEUE)
    public void onMessage(Message message) {
        consumer.consume("delivery", QUEUE, message, handler);
    }
}
