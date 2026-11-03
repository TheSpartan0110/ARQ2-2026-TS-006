package com.eatsnow.notifications.domain.exception;

/** The contact lookup or the email provider failed in a way that may succeed later (timeout, 5xx, SMTP down). */
public class TransientNotificationException extends RuntimeException {
    public TransientNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
