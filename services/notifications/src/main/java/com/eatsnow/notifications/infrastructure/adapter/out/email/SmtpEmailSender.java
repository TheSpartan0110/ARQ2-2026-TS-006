package com.eatsnow.notifications.infrastructure.adapter.out.email;

import com.eatsnow.notifications.domain.exception.TransientNotificationException;
import com.eatsnow.notifications.domain.port.out.EmailSenderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP adapter (ADR 006): Mailpit locally, Amazon SES over SMTP in production. The recipient is never logged. */
@Component
public class SmtpEmailSender implements EmailSenderPort {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, @Value("${eatsnow.notifications.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new TransientNotificationException("The email provider failed", e);
        }
    }
}
