package com.eatsnow.payments.infrastructure.adapter.out.messaging;

import com.eatsnow.messaging.consumer.EnvelopeConsumer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Entry point of the `payments.commands` queue (Spec 4.2.1). */
@Component
public class PaymentCommandListener {
    static final String QUEUE = "payments.commands";

    private final EnvelopeConsumer consumer;
    private final PaymentCommandHandler handler;

    public PaymentCommandListener(EnvelopeConsumer consumer, PaymentCommandHandler handler) {
        this.consumer = consumer;
        this.handler = handler;
    }

    @RabbitListener(queues = QUEUE)
    public void onMessage(Message message) {
        consumer.consume("payments", QUEUE, message, handler);
    }
}
