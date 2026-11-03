package com.eatsnow.orders.infrastructure.adapter.in.web.dto;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.orders.domain.model.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Order schema of Spec 2.2. It never includes the payment method token. */
public record OrderResponseDto(UUID id, UUID customerId, UUID restaurantId, List<OrderItemDto> items, Money total,
                               AddressDto deliveryAddress, String status, String paymentStatus, UUID correlationId,
                               int version, Instant createdAt, Instant updatedAt) {

    public static OrderResponseDto fromDomain(Order o) {
        return new OrderResponseDto(o.id(), o.customerId(), o.restaurantId(),
                o.items().stream().map(OrderItemDto::fromDomain).toList(), o.total(),
                AddressDto.fromDomain(o.deliveryAddress()), o.status().name(), o.paymentStatus().name(),
                o.correlationId(), o.version(), o.createdAt(), o.updatedAt());
    }
}
