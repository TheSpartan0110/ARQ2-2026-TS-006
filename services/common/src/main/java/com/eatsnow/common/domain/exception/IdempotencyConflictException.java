package com.eatsnow.common.domain.exception;

public class IdempotencyConflictException extends DomainException {
    public IdempotencyConflictException(String message) {
        super("IDEMPOTENCY_CONFLICT", message, 409);
    }
}
