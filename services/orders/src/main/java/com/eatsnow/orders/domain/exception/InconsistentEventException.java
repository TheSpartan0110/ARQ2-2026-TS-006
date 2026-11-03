package com.eatsnow.orders.domain.exception;

/**
 * A consumed event contradicts what Orders knows (unknown order, amount mismatch). Retrying cannot help, so the
 * messaging adapter quarantines the message in the DLQ (Spec 4.3 item 3).
 */
public class InconsistentEventException extends RuntimeException {
    public InconsistentEventException(String message) {
        super(message);
    }
}
