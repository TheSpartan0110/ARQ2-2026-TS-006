package com.eatsnow.delivery.domain.port.in;

import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.delivery.domain.model.Delivery;
import com.eatsnow.delivery.domain.model.DeliveryStatus;
import java.util.UUID;

/** GET /deliveries and /deliveries/{id}: only deliveries assigned to the caller (Spec 3.6). */
public interface GetDeliveriesQuery {
    PageResult<Delivery> list(UUID driverId, DeliveryStatus status, int limit, String cursor);

    /** @throws com.eatsnow.common.domain.exception.EntityNotFoundException absent or not the caller's (404) */
    Delivery get(UUID driverId, UUID deliveryId);
}
