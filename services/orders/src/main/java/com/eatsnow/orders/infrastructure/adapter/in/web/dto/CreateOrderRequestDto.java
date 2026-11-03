package com.eatsnow.orders.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The client cannot send customerId, roles, prices, totals or status: they come from the token and the quote. */
public record CreateOrderRequestDto(
        @NotBlank(message = "quoteToken is required") String quoteToken,
        @NotNull(message = "deliveryAddress is required") @Valid AddressDto deliveryAddress,
        @NotBlank(message = "paymentMethodToken is required")
        @Size(max = 2048, message = "paymentMethodToken must have at most 2048 characters") String paymentMethodToken) {
}
