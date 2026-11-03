package com.eatsnow.orders.domain.exception;

import com.eatsnow.common.domain.exception.DomainException;

/** The quote token is not trustworthy or does not match the caller (Spec 3.4: 422 INVALID_QUOTE). */
public class InvalidQuoteException extends DomainException {
    public InvalidQuoteException(String message) {
        super("INVALID_QUOTE", message, 422);
    }
}
