package com.eatsnow.delivery.domain.port.in;

import com.eatsnow.delivery.domain.model.DeliveryAddress;
import java.util.UUID;

/** AssignDelivery command: creates the UNASSIGNED delivery (Spec 3.6). */
public interface RegisterDeliveryUseCase {

    record Command(UUID orderId, UUID restaurantId, DeliveryAddress deliveryAddress, UUID correlationId,
                   UUID commandMessageId) { }

    /** @return false when a delivery already exists for the order (unique per orderId) */
    boolean register(Command command);
}
