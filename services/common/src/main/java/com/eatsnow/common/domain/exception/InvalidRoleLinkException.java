package com.eatsnow.common.domain.exception;

/**
 * Thrown when role link constraints are violated (Spec 3.2: RESTAURANT requires restaurantId,
 * DRIVER generates driverId, other combinations return 422).
 */
public class InvalidRoleLinkException extends DomainException {

    public InvalidRoleLinkException(String message) {
        super("INVALID_ROLE_LINK", message, 422);
    }
}
