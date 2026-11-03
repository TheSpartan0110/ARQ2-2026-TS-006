package com.eatsnow.orders.domain.exception;

import com.eatsnow.common.domain.exception.DomainException;

/** Spec 3.4: 422 QUOTE_EXPIRED. */
public class QuoteExpiredException extends DomainException {
    public QuoteExpiredException() {
        super("QUOTE_EXPIRED", "The quote has expired; request a new checkout quote", 422);
    }
}
