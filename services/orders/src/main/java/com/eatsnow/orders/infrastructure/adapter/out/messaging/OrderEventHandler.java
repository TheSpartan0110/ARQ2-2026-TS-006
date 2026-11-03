package com.eatsnow.orders.infrastructure.adapter.out.messaging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.common.messaging.MessageEnvelope;
import com.eatsnow.messaging.consumer.EnvelopeHandler;
import com.eatsnow.messaging.contract.Payloads;
import com.eatsnow.messaging.support.MessageJson;
import com.eatsnow.messaging.support.MessageTypes;
import com.eatsnow.messaging.support.PoisonMessageException;
import com.eatsnow.orders.domain.exception.InconsistentEventException;
import com.eatsnow.orders.domain.model.OrderEvent;
import com.eatsnow.orders.domain.port.in.HandleOrderEventUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Translates a consumed message into a call to the order state machine use case. */
@Component
public class OrderEventHandler implements EnvelopeHandler {
    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);

    private final HandleOrderEventUseCase useCase;

    public OrderEventHandler(HandleOrderEventUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void handle(MessageEnvelope<JsonNode> env) {
        try {
            switch (env.messageType()) {
                case MessageTypes.ORDER_CREATED -> useCase.onOrderCreated(env.orderId(), env.messageId());
                case MessageTypes.PAYMENT_AUTHORIZED -> {
                    Payloads.PaymentAuthorized p = MessageJson.readPayload(env, Payloads.PaymentAuthorized.class);
                    useCase.onPaymentAuthorized(env.orderId(), p.paymentId(), p.total(), env.messageId());
                }
                case MessageTypes.PAYMENT_FAILED -> {
                    MessageJson.readPayload(env, Payloads.PaymentFailed.class);   // validates the payload shape
                    useCase.onPaymentFailed(env.orderId(), env.messageId());
                }
                case MessageTypes.PAYMENT_PENDING_RECONCILIATION -> useCase.onPaymentPendingReconciliation(
                        env.orderId(),
                        MessageJson.readPayload(env, Payloads.PaymentPendingReconciliation.class).operation(),
                        env.messageId());
                case MessageTypes.RESTAURANT_ACCEPTED -> useCase.handle(new OrderEvent.RestaurantAccepted(
                        env.orderId(), env.messageId(),
                        MessageJson.readPayload(env, Payloads.RestaurantDecision.class).restaurantId()));
                case MessageTypes.RESTAURANT_REJECTED -> {
                    Payloads.RestaurantRejected p = MessageJson.readPayload(env, Payloads.RestaurantRejected.class);
                    useCase.handle(new OrderEvent.RestaurantRejected(env.orderId(), env.messageId(),
                            p.restaurantId(), p.reason()));
                }
                case MessageTypes.RESTAURANT_PREPARING -> useCase.handle(new OrderEvent.RestaurantPreparing(
                        env.orderId(), env.messageId(),
                        MessageJson.readPayload(env, Payloads.RestaurantDecision.class).restaurantId()));
                case MessageTypes.DELIVERY_ASSIGNED -> {
                    Payloads.DeliveryAssigned p = MessageJson.readPayload(env, Payloads.DeliveryAssigned.class);
                    useCase.handle(new OrderEvent.DeliveryAssigned(env.orderId(), env.messageId(),
                            p.deliveryId(), p.driverId()));
                }
                case MessageTypes.DELIVERY_PICKED_UP -> {
                    Payloads.DeliveryPickedUp p = MessageJson.readPayload(env, Payloads.DeliveryPickedUp.class);
                    useCase.handle(new OrderEvent.DeliveryPickedUp(env.orderId(), env.messageId(),
                            p.deliveryId(), p.driverId()));
                }
                case MessageTypes.DELIVERY_COMPLETED -> {
                    Payloads.DeliveryCompleted p = MessageJson.readPayload(env, Payloads.DeliveryCompleted.class);
                    useCase.handle(new OrderEvent.DeliveryCompleted(env.orderId(), env.messageId(),
                            p.deliveryId(), p.driverId()));
                }
                case MessageTypes.PAYMENT_REFUNDED -> {
                    Payloads.PaymentRefunded p = MessageJson.readPayload(env, Payloads.PaymentRefunded.class);
                    useCase.handle(new OrderEvent.PaymentRefunded(env.orderId(), env.messageId(),
                            p.paymentId(), p.amount()));
                }
                default -> log.warn("Message type not handled yet", kv("operation", "consume " + env.messageType()),
                        kv("outcome", "PENDING"));
            }
        } catch (InconsistentEventException e) {
            throw new PoisonMessageException(e.getMessage(), e);        // retrying cannot help: DLQ
        }
    }
}
