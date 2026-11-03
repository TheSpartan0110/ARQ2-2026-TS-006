package com.eatsnow.restaurants.domain.port.out;

import com.eatsnow.restaurants.domain.model.RestaurantOrder;

/**
 * Outgoing events, written to the transactional outbox inside the caller's transaction (ADR 001). They use the stored
 * workflow correlationId, causationId null (HTTP-triggered), aggregateId = orderId and aggregateVersion = new version.
 */
public interface RestaurantEventPublisherPort {
    void accepted(RestaurantOrder order);

    void rejected(RestaurantOrder order);

    void preparing(RestaurantOrder order);
}
