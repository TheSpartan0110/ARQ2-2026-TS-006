package com.eatsnow.notifications.domain.exception;

/** Identity has no contact for the customer. Permanent: retrying cannot help. */
public class ContactNotFoundException extends RuntimeException {
    public ContactNotFoundException(String message) {
        super(message);
    }
}
