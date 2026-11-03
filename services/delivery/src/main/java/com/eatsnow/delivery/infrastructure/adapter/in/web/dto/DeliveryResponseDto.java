package com.eatsnow.delivery.infrastructure.adapter.in.web.dto;

import com.eatsnow.delivery.domain.model.Delivery;
import java.time.Instant;
import java.util.UUID;

/** Delivery schema of Spec 2.2. The address is only ever shown to the assigned driver. */
public record DeliveryResponseDto(UUID id, UUID orderId, UUID restaurantId, UUID driverId, AddressDto deliveryAddress,
                                  String status, int version, Instant createdAt, Instant updatedAt) {
    public static DeliveryResponseDto fromDomain(Delivery d) {
        return new DeliveryResponseDto(d.id(), d.orderId(), d.restaurantId(), d.driverId(),
                AddressDto.fromDomain(d.deliveryAddress()), d.status().name(), d.version(), d.createdAt(),
                d.updatedAt());
    }
}
