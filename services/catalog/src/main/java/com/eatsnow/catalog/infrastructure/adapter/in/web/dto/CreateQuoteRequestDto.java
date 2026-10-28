package com.eatsnow.catalog.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateQuoteRequestDto(
        @NotNull(message = "restaurantId is required")
        UUID restaurantId,

        @NotEmpty(message = "items cannot be empty")
        @Size(min = 1, max = 50, message = "items must contain between 1 and 50 entries")
        List<@Valid RequestedItemRequestDto> items
) {
}
