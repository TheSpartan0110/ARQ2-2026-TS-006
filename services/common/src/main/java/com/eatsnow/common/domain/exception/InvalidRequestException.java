package com.eatsnow.common.domain.exception;

public class InvalidRequestException extends DomainException {
    public InvalidRequestException(String message) {
        super("INVALID_REQUEST", message, 400);
    }
}
