package com.eatsnow.restaurants.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The reason is mandatory on rejection (Spec 2.2): 400 INVALID_REQUEST when missing or empty. */
public record RejectRequestDto(
        @NotNull(message = "expectedVersion is required") @Min(value = 1, message = "expectedVersion must be at least 1")
        Integer expectedVersion,
        @NotBlank(message = "reason is required") @Size(max = 300, message = "reason must have at most 300 characters")
        String reason) {
}
