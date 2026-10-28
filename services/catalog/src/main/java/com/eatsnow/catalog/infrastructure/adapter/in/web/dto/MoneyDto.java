package com.eatsnow.catalog.infrastructure.adapter.in.web.dto;

import com.eatsnow.common.domain.model.Money;

public record MoneyDto(
        long amountMinor,
        String currency
) {
    public static MoneyDto fromDomain(Money money) {
        return new MoneyDto(money.amountMinor(), money.currency());
    }

    public Money toDomain() {
        return new Money(amountMinor, currency);
    }
}
