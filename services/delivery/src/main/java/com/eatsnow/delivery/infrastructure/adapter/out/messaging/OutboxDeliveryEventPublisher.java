package com.eatsnow.delivery.infrastructure.adapter.out.messaging;

import com.eatsnow.delivery.domain.model.Delivery;
import com.eatsnow.delivery.domain.port.out.DeliveryEventPublisherPort;
import com.eatsnow.messaging.contract.Payloads;
import com.eatsnow.messaging.outbox.OutboxWriter;
import com.eatsnow.messaging.support.MessageTypes;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Writes the delivery events to the transactional outbox (ADR 001); the relay publishes them after the commit. */
@Component
public class OutboxDeliveryEventPublisher implements DeliveryEventPublisherPort {
    private static final String PRODUCER = "delivery";

    private final OutboxWriter outbox;

    public OutboxDeliveryEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    /** System assignment: causationId is the AssignDelivery messageId. */
    @Override
    public void assigned(Delivery d) {
        publish(d, MessageTypes.DELIVERY_ASSIGNED, d.commandMessageId(),
                new Payloads.DeliveryAssigned(d.id(), d.driverId()));
    }

    /** Driver action: causationId null. */
    @Override
    public void pickedUp(Delivery d) {
        publish(d, MessageTypes.DELIVERY_PICKED_UP, null,
                new Payloads.DeliveryPickedUp(d.id(), d.driverId(), d.updatedAt()));
    }

    @Override
    public void completed(Delivery d) {
        publish(d, MessageTypes.DELIVERY_COMPLETED, null,
                new Payloads.DeliveryCompleted(d.id(), d.driverId(), d.updatedAt()));
    }

    /** Stored workflow correlationId, aggregateId = deliveryId, aggregateVersion = new Delivery version (Spec 3.6). */
    private void publish(Delivery d, String messageType, UUID causationId, Object payload) {
        outbox.enqueue(MessageTypes.EVENTS, messageType, PRODUCER, d.orderId(), d.correlationId(), causationId,
                d.id(), d.version(), payload);
    }
}
