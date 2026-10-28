package com.eatsnow.catalog.infrastructure.adapter.in.web.dto;

import com.eatsnow.catalog.domain.model.Restaurant;

import java.util.UUID;

public record RestaurantResponseDto(
        UUID id,
        String name,
        boolean available
) {
    public static RestaurantResponseDto fromDomain(Restaurant restaurant) {
        return new RestaurantResponseDto(restaurant.id(), restaurant.name(), restaurant.available());
    }
}
