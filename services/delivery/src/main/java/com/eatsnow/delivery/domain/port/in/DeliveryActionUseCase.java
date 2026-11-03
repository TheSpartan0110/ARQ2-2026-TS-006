package com.eatsnow.delivery.domain.port.in;

import com.eatsnow.delivery.domain.model.Delivery;
import java.util.UUID;

/** Pickup and completion by the assigned driver (Spec 3.6), atomic with the idempotency record and the outbox event. */
public interface DeliveryActionUseCase {

    record Command(UUID driverId, UUID subjectId, UUID deliveryId, int expectedVersion, UUID idempotencyKey,
                   String requestPath, String rawPayload) { }

    Delivery pickUp(Command command);

    Delivery complete(Command command);
}
