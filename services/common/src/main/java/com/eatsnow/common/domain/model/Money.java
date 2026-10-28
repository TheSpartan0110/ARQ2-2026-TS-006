package com.eatsnow.common.domain.model;

import java.util.Objects;

/**
 * Common Money value object using minor currency units (Spec 2.1).
 * Example: COP 25,000 is represented as 2,500,000 minor units.
 */
public record Money(long amountMinor, String currency) {

    public static final String DEFAULT_CURRENCY = "COP";

    public Money {
        if (amountMinor < 0) {
            throw new IllegalArgumentException("amountMinor must be greater than or equal to 0");
        }
        if (currency == null || currency.isBlank()) {
            currency = DEFAULT_CURRENCY;
        }
    }

    public static Money cop(long amountMinor) {
        return new Money(amountMinor, DEFAULT_CURRENCY);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative");
        }
        return new Money(this.amountMinor * quantity, this.currency);
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "other money cannot be null");
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add money with different currencies: " + this.currency + " vs " + other.currency);
        }
        return new Money(this.amountMinor + other.amountMinor, this.currency);
    }
}
