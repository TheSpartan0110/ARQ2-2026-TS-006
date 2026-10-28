package com.eatsnow.common.domain.exception;

/**
 * Thrown when attempting to register or create a profile with an existing email (Spec 3.2).
 */
public class EmailAlreadyRegisteredException extends DomainException {

    public EmailAlreadyRegisteredException(String message) {
        super("EMAIL_ALREADY_REGISTERED", message, 409);
    }
}
