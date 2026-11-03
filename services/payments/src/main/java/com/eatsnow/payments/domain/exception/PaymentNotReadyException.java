package com.eatsnow.payments.domain.exception;

/** The payment has no definitive outcome yet, so a refund cannot start. Transient: the command is retried. */
public class PaymentNotReadyException extends RuntimeException {
    public PaymentNotReadyException(String message) {
        super(message);
    }
}
