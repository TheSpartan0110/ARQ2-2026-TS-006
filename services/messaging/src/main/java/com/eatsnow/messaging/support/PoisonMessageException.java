package com.eatsnow.messaging.support;

/** The message can never succeed (unknown order, amount mismatch...). It is sent straight to the DLQ. */
public class PoisonMessageException extends RuntimeException {
    public PoisonMessageException(String message) {
        super(message);
    }

    public PoisonMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
