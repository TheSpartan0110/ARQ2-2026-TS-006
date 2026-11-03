package com.eatsnow.common.domain.exception;

/** The caller is authenticated but cannot use this endpoint (Spec 3.1: 403 FORBIDDEN). */
public class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, 403);
    }
}
