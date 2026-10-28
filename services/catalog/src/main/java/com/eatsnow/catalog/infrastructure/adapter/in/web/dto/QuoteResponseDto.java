package com.eatsnow.catalog.infrastructure.adapter.in.web.dto;

import com.eatsnow.catalog.domain.model.Quote;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuoteResponseDto(
        UUID id,
        UUID customerId,
        UUID restaurantId,
        List<OrderItemDto> items,
        MoneyDto total,
        Instant expiresAt,
        String quoteToken
) {
    public static QuoteResponseDto fromDomain(Quote quote) {
        return new QuoteResponseDto(
                quote.id(),
                quote.customerId(),
                quote.restaurantId(),
                quote.items().stream().map(OrderItemDto::fromDomain).toList(),
                MoneyDto.fromDomain(quote.total()),
                quote.expiresAt(),
                quote.quoteToken()
        );
    }
}
