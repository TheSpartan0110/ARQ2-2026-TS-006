package com.eatsnow.notifications.infrastructure.adapter.out.messaging;

import com.eatsnow.messaging.consumer.EnvelopeConsumer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Entry point of the `notifications.events` queue (Spec 4.2.1): OrderStatusChanged only. */
@Component
public class NotificationEventListener {
    static final String QUEUE = "notifications.events";

    private final EnvelopeConsumer consumer;
    private final NotificationEventHandler handler;

    public NotificationEventListener(EnvelopeConsumer consumer, NotificationEventHandler handler) {
        this.consumer = consumer;
        this.handler = handler;
    }

    @RabbitListener(queues = QUEUE)
    public void onMessage(Message message) {
        consumer.consume("notifications", QUEUE, message, handler);
    }
}
