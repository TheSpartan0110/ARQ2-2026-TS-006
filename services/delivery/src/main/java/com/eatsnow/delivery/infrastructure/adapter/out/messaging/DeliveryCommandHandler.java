package com.eatsnow.delivery.infrastructure.adapter.out.messaging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.common.messaging.MessageEnvelope;
import com.eatsnow.delivery.domain.model.DeliveryAddress;
import com.eatsnow.delivery.domain.port.in.RegisterDeliveryUseCase;
import com.eatsnow.messaging.consumer.EnvelopeHandler;
import com.eatsnow.messaging.contract.Payloads;
import com.eatsnow.messaging.support.MessageJson;
import com.eatsnow.messaging.support.MessageTypes;
import com.eatsnow.messaging.support.PoisonMessageException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Translates AssignDelivery into the delivery registration use case. The address is never logged. */
@Component
public class DeliveryCommandHandler implements EnvelopeHandler {
    private static final Logger log = LoggerFactory.getLogger(DeliveryCommandHandler.class);

    private final RegisterDeliveryUseCase register;

    public DeliveryCommandHandler(RegisterDeliveryUseCase register) {
        this.register = register;
    }

    @Override
    public void handle(MessageEnvelope<JsonNode> env) {
        if (!MessageTypes.ASSIGN_DELIVERY.equals(env.messageType())) {
            log.warn("Command not handled", kv("operation", "consume " + env.messageType()), kv("outcome", "FAILURE"));
            return;
        }
        Payloads.AssignDelivery command = MessageJson.readPayload(env, Payloads.AssignDelivery.class);
        if (command.restaurantId() == null || command.deliveryAddress() == null) {
            throw new PoisonMessageException("AssignDelivery needs a restaurantId and a deliveryAddress");
        }
        register.register(new RegisterDeliveryUseCase.Command(env.orderId(), command.restaurantId(),
                new DeliveryAddress(command.deliveryAddress().line1(), command.deliveryAddress().city(),
                        command.deliveryAddress().instructions()),
                env.correlationId(), env.messageId()));
    }
}
