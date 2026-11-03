package com.eatsnow.restaurants.infrastructure.adapter.out.messaging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.common.messaging.MessageEnvelope;
import com.eatsnow.messaging.consumer.EnvelopeHandler;
import com.eatsnow.messaging.contract.Payloads;
import com.eatsnow.messaging.support.MessageJson;
import com.eatsnow.messaging.support.MessageTypes;
import com.eatsnow.messaging.support.PoisonMessageException;
import com.eatsnow.restaurants.domain.model.OrderLine;
import com.eatsnow.restaurants.domain.port.in.RegisterRestaurantOrderUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Translates RequestRestaurantAcceptance into the restaurant inbox use case. */
@Component
public class RestaurantCommandHandler implements EnvelopeHandler {
    private static final Logger log = LoggerFactory.getLogger(RestaurantCommandHandler.class);

    private final RegisterRestaurantOrderUseCase register;

    public RestaurantCommandHandler(RegisterRestaurantOrderUseCase register) {
        this.register = register;
    }

    @Override
    public void handle(MessageEnvelope<JsonNode> env) {
        if (!MessageTypes.REQUEST_RESTAURANT_ACCEPTANCE.equals(env.messageType())) {
            log.warn("Command not handled", kv("operation", "consume " + env.messageType()), kv("outcome", "FAILURE"));
            return;
        }
        Payloads.RequestRestaurantAcceptance command =
                MessageJson.readPayload(env, Payloads.RequestRestaurantAcceptance.class);
        if (command.restaurantId() == null || command.items() == null || command.items().isEmpty()) {
            throw new PoisonMessageException("RequestRestaurantAcceptance needs a restaurantId and items");
        }
        List<OrderLine> items = command.items().stream()
                .map(i -> new OrderLine(i.menuItemId(), i.name(), i.quantity(), i.unitPrice()))
                .toList();
        register.register(env.orderId(), command.restaurantId(), items, env.correlationId());
    }
}
