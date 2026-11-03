package com.eatsnow.delivery.domain.port.out;

import com.eatsnow.delivery.domain.model.Delivery;

/**
 * Outgoing events, written to the transactional outbox inside the caller's transaction (ADR 001). They use the stored
 * workflow correlationId, aggregateId = deliveryId and aggregateVersion = the new Delivery version. causationId is
 * the AssignDelivery messageId for system assignment and null for driver actions (Spec 3.6).
 */
public interface DeliveryEventPublisherPort {
    void assigned(Delivery delivery);

    void pickedUp(Delivery delivery);

    void completed(Delivery delivery);
}
