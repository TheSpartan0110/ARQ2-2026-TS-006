package com.eatsnow.restaurants.domain.port.in;

import com.eatsnow.restaurants.domain.model.RestaurantOrder;
import java.util.UUID;

/** Accept, reject and start preparation (Spec 3.5). Each one is atomic with its idempotency record and outbox event. */
public interface DecideRestaurantOrderUseCase {

    /**
     * @param subjectId  authenticated user, part of the idempotency scope
     * @param reason     only used by reject
     * @param rawPayload canonical request body used to hash the request for idempotency
     */
    record Command(UUID restaurantId, UUID subjectId, UUID orderId, int expectedVersion, String reason,
                   UUID idempotencyKey, String requestPath, String rawPayload) { }

    RestaurantOrder accept(Command command);

    RestaurantOrder reject(Command command);

    RestaurantOrder startPreparation(Command command);
}
