package com.eatsnow.catalog.infrastructure.adapter.in.web.dto;

import com.eatsnow.catalog.domain.model.MenuItem;

import java.util.UUID;

public record MenuItemResponseDto(
        UUID id,
        UUID restaurantId,
        String name,
        MoneyDto price,
        boolean available,
        int version
) {
    public static MenuItemResponseDto fromDomain(MenuItem item) {
        return new MenuItemResponseDto(
                item.id(),
                item.restaurantId(),
                item.name(),
                MoneyDto.fromDomain(item.price()),
                item.available(),
                item.version()
        );
    }
}
