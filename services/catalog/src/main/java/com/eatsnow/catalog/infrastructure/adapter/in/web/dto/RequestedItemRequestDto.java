package com.eatsnow.catalog.infrastructure.adapter.in.web.dto;

import com.eatsnow.catalog.domain.model.RequestedItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RequestedItemRequestDto(
        @NotNull(message = "menuItemId is required")
        UUID menuItemId,

        @Min(value = 1, message = "quantity must be between 1 and 20")
        @Max(value = 20, message = "quantity must be between 1 and 20")
        int quantity
) {
    public RequestedItem toDomain() {
        return new RequestedItem(menuItemId, quantity);
    }
}
