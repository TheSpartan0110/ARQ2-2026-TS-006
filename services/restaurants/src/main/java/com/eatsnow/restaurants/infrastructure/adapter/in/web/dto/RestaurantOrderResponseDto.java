package com.eatsnow.restaurants.infrastructure.adapter.in.web.dto;

import com.eatsnow.restaurants.domain.model.RestaurantOrder;
import java.util.List;
import java.util.UUID;

/** RestaurantOrder schema of Spec 2.2. The delivery address is never part of it. */
public record RestaurantOrderResponseDto(UUID orderId, UUID restaurantId, List<OrderLineDto> items, String status,
                                         String reason, int version) {
    public static RestaurantOrderResponseDto fromDomain(RestaurantOrder o) {
        return new RestaurantOrderResponseDto(o.orderId(), o.restaurantId(),
                o.items().stream().map(OrderLineDto::fromDomain).toList(), o.status().name(), o.reason(), o.version());
    }
}
