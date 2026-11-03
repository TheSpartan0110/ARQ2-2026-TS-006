package com.eatsnow.restaurants.infrastructure.adapter.in.web.dto;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.restaurants.domain.model.OrderLine;
import java.util.UUID;

public record OrderLineDto(UUID menuItemId, String name, int quantity, Money unitPrice) {
    public static OrderLineDto fromDomain(OrderLine line) {
        return new OrderLineDto(line.menuItemId(), line.name(), line.quantity(), line.unitPrice());
    }
}
