package com.eatsnow.common.domain.exception;

/**
 * Thrown when an invalid state transition or prohibited action is requested (Spec 2.4 & 3.2).
 */
public class InvalidTransitionException extends DomainException {

    public InvalidTransitionException(String message) {
        super("INVALID_TRANSITION", message, 409);
    }
}
