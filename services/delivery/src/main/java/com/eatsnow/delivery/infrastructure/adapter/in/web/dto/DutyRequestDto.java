package com.eatsnow.delivery.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DutyRequestDto(
        @NotNull(message = "onDuty is required") Boolean onDuty,
        @NotNull(message = "expectedVersion is required") @Min(value = 1, message = "expectedVersion must be at least 1")
        Integer expectedVersion) {
}
