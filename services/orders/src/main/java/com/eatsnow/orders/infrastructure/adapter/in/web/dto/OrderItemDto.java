package com.eatsnow.orders.infrastructure.adapter.in.web.dto;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.orders.domain.model.OrderItem;
import java.util.UUID;

public record OrderItemDto(UUID menuItemId, String name, int quantity, Money unitPrice) {
    public static OrderItemDto fromDomain(OrderItem item) {
        return new OrderItemDto(item.menuItemId(), item.name(), item.quantity(), item.unitPrice());
    }
}
