package com.eatsnow.delivery.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body of pickup and completion: only the optimistic-concurrency version. */
public record ActionRequestDto(
        @NotNull(message = "expectedVersion is required") @Min(value = 1, message = "expectedVersion must be at least 1")
        Integer expectedVersion) {
}
