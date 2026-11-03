package com.eatsnow.delivery.infrastructure.adapter.in.web.dto;

import com.eatsnow.delivery.domain.model.Driver;
import java.util.UUID;

/** Driver schema of Spec 2.2. */
public record DriverResponseDto(UUID id, UUID profileId, boolean onDuty, UUID activeDeliveryId, int version) {
    public static DriverResponseDto fromDomain(Driver d) {
        return new DriverResponseDto(d.id(), d.profileId(), d.onDuty(), d.activeDeliveryId(), d.version());
    }
}
