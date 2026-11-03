package com.eatsnow.restaurants.domain.port.in;

import com.eatsnow.restaurants.domain.model.OrderLine;
import java.util.List;
import java.util.UUID;

/** RequestRestaurantAcceptance command: creates the PENDING inbox entry (Spec 3.5). */
public interface RegisterRestaurantOrderUseCase {
    /** @return false when an entry already exists for the order: a duplicate leaves it unchanged (Spec 4.2.2) */
    boolean register(UUID orderId, UUID restaurantId, List<OrderLine> items, UUID correlationId);
}
