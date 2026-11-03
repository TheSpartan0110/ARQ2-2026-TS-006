package com.eatsnow.orders.infrastructure.adapter.out.messaging;

import com.eatsnow.messaging.consumer.EnvelopeConsumer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Entry point of the `orders.events` queue (Spec 4.2.1). All reliability logic lives in EnvelopeConsumer. */
@Component
public class OrdersEventListener {
    static final String QUEUE = "orders.events";

    private final EnvelopeConsumer consumer;
    private final OrderEventHandler handler;

    public OrdersEventListener(EnvelopeConsumer consumer, OrderEventHandler handler) {
        this.consumer = consumer;
        this.handler = handler;
    }

    @RabbitListener(queues = QUEUE)
    public void onMessage(Message message) {
        consumer.consume("orders", QUEUE, message, handler);
    }
}
