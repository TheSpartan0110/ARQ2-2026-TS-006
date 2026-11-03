package com.eatsnow.common.domain.exception;

import java.util.Map;

/**
 * Thrown when an invalid state transition or prohibited action is requested (Spec 2.4 & 3.2).
 */
public class InvalidTransitionException extends DomainException {

    public InvalidTransitionException(String message) {
        super("INVALID_TRANSITION", message, 409);
    }

    /** Spec 3.5: `details` carries the status the resource is currently in. */
    public InvalidTransitionException(String message, String currentStatus) {
        super("INVALID_TRANSITION", message, 409, Map.of("currentStatus", currentStatus));
    }
}
