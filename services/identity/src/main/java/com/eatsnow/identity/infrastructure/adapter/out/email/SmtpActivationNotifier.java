package com.eatsnow.identity.infrastructure.adapter.out.email;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.out.ActivationNotifierPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Emails the activation link over SMTP (Mailpit locally). It is sent only after the profile commits, so a rolled-back
 * creation never mails a link. A failed send is logged without the recipient or the token (Spec 6.5).
 */
@Component
public class SmtpActivationNotifier implements ActivationNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpActivationNotifier.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String publicUrl;

    public SmtpActivationNotifier(JavaMailSender mailSender,
                                  @Value("${eatsnow.identity.mail-from}") String from,
                                  @Value("${eatsnow.identity.public-url}") String publicUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.publicUrl = publicUrl;
    }

    @Override
    public void sendActivationLink(Profile profile, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(profile.email());
        message.setSubject("Activate your EatsNow account");
        message.setText("Hello " + profile.displayName() + ",\n\n"
                + "An administrator created your EatsNow account. Set your password within 72 hours:\n\n"
                + publicUrl + "/activate?token=" + rawToken + "\n\n"
                + "If you did not expect this email, ignore it.");

        Runnable send = () -> {
            try {
                mailSender.send(message);
            } catch (MailException e) {
                log.warn("Activation email could not be sent", kv("operation", "send activation email"),
                        kv("outcome", "FAILURE"), kv("profileId", profile.id()),
                        kv("errorCode", e.getClass().getSimpleName()));
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
