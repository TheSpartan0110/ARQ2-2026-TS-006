package com.eatsnow.notifications.infrastructure.adapter.out.messaging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.common.messaging.MessageEnvelope;
import com.eatsnow.messaging.consumer.EnvelopeHandler;
import com.eatsnow.messaging.contract.Payloads;
import com.eatsnow.messaging.support.MessageJson;
import com.eatsnow.messaging.support.MessageTypes;
import com.eatsnow.messaging.support.PoisonMessageException;
import com.eatsnow.notifications.domain.port.in.ProcessOrderStatusChangeUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Translates OrderStatusChanged into the notification recording use case. */
@Component
public class NotificationEventHandler implements EnvelopeHandler {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

    private final ProcessOrderStatusChangeUseCase useCase;

    public NotificationEventHandler(ProcessOrderStatusChangeUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void handle(MessageEnvelope<JsonNode> env) {
        if (!MessageTypes.ORDER_STATUS_CHANGED.equals(env.messageType())) {
            log.warn("Message not handled", kv("operation", "consume " + env.messageType()), kv("outcome", "FAILURE"));
            return;
        }
        Payloads.OrderStatusChanged payload = MessageJson.readPayload(env, Payloads.OrderStatusChanged.class);
        if (payload.customerId() == null || payload.status() == null) {
            throw new PoisonMessageException("OrderStatusChanged needs a customerId and a status");
        }
        useCase.process(new ProcessOrderStatusChangeUseCase.Change(env.orderId(), payload.customerId(),
                env.aggregateVersion(), payload.previousStatus(), payload.status(), payload.paymentStatus(),
                env.correlationId()));
    }
}
