package com.eatsnow.common.domain.exception;

public class RequestInProgressException extends DomainException {
    public RequestInProgressException(String message) {
        super("REQUEST_IN_PROGRESS", message, 409);
    }
}
