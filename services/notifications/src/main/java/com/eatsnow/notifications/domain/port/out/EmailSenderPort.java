package com.eatsnow.notifications.domain.port.out;

/** SMTP adapter: provider-neutral, Mailpit locally and Amazon SES in production (ADR 006). */
public interface EmailSenderPort {
    /** @throws com.eatsnow.notifications.domain.exception.TransientNotificationException the provider failed */
    void send(String to, String subject, String body);
}
