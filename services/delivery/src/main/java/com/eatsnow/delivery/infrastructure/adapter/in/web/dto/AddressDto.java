package com.eatsnow.delivery.infrastructure.adapter.in.web.dto;

import com.eatsnow.delivery.domain.model.DeliveryAddress;

public record AddressDto(String line1, String city, String instructions) {
    public static AddressDto fromDomain(DeliveryAddress address) {
        return new AddressDto(address.line1(), address.city(), address.instructions());
    }
}
