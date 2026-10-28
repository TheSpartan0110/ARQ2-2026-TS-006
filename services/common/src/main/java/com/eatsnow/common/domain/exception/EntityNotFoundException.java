package com.eatsnow.common.domain.exception;

public class EntityNotFoundException extends DomainException {
    public EntityNotFoundException(String message) {
        super("NOT_FOUND", message, 404);
    }
}
