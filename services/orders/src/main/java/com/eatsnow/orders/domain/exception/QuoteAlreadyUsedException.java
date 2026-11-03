package com.eatsnow.orders.domain.exception;

import com.eatsnow.common.domain.exception.DomainException;

/** Spec 3.4: 409 QUOTE_ALREADY_USED. Quote uniqueness is permanent. */
public class QuoteAlreadyUsedException extends DomainException {
    public QuoteAlreadyUsedException() {
        super("QUOTE_ALREADY_USED", "This quote already produced an order", 409);
    }
}
