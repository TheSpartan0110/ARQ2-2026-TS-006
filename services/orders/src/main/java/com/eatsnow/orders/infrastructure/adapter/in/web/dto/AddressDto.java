package com.eatsnow.orders.infrastructure.adapter.in.web.dto;

import com.eatsnow.orders.domain.model.DeliveryAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressDto(
        @NotBlank(message = "deliveryAddress.line1 is required") @Size(max = 200) String line1,
        @NotBlank(message = "deliveryAddress.city is required") @Size(max = 100) String city,
        @Size(max = 300) String instructions) {

    public DeliveryAddress toDomain() {
        return new DeliveryAddress(line1, city, instructions);
    }

    public static AddressDto fromDomain(DeliveryAddress address) {
        return new AddressDto(address.line1(), address.city(), address.instructions());
    }
}
