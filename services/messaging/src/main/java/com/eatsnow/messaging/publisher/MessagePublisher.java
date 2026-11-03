package com.eatsnow.messaging.publisher;

import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Publishes a persistent message and blocks until the broker confirms it (Spec 4.1: publisher confirms). */
public class MessagePublisher {
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final RabbitTemplate template;

    public MessagePublisher(RabbitTemplate template) {
        this.template = template;
    }

    public void publish(String exchange, String routingKey, byte[] body, UUID messageId, Map<String, Object> headers) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setMessageId(messageId.toString());
        headers.forEach(props::setHeader);
        Message message = new Message(body, props);

        // invoke() keeps send + waitForConfirms on the same channel, which publisher confirms require.
        template.invoke(ops -> {
            ops.send(exchange, routingKey, message);
            ops.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }
}
