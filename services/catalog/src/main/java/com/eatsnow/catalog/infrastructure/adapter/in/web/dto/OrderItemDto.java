package com.eatsnow.catalog.infrastructure.adapter.in.web.dto;

import com.eatsnow.catalog.domain.model.OrderItem;

import java.util.UUID;

public record OrderItemDto(
        UUID menuItemId,
        String name,
        int quantity,
        MoneyDto unitPrice
) {
    public static OrderItemDto fromDomain(OrderItem item) {
        return new OrderItemDto(
                item.menuItemId(),
                item.name(),
                item.quantity(),
                MoneyDto.fromDomain(item.unitPrice())
        );
    }
}
