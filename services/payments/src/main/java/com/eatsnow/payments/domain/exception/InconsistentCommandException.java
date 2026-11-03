package com.eatsnow.payments.domain.exception;

/** A command contradicts what Payments knows (unknown payment, amount or order mismatch): quarantined in the DLQ. */
public class InconsistentCommandException extends RuntimeException {
    public InconsistentCommandException(String message) {
        super(message);
    }
}
