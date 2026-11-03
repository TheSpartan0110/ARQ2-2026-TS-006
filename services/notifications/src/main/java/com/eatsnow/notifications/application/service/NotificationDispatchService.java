package com.eatsnow.notifications.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.notifications.domain.exception.ContactNotFoundException;
import com.eatsnow.notifications.domain.exception.TransientNotificationException;
import com.eatsnow.notifications.domain.model.CustomerContact;
import com.eatsnow.notifications.domain.model.Notification;
import com.eatsnow.notifications.domain.port.in.DispatchNotificationsUseCase;
import com.eatsnow.notifications.domain.port.out.ContactLookupPort;
import com.eatsnow.notifications.domain.port.out.EmailSenderPort;
import com.eatsnow.notifications.domain.port.out.NotificationRepositoryPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends recorded notifications. Not transactional on purpose: the contact lookup and the SMTP call are external and
 * never hold a database transaction (ADR 006). Transient failures are retried after 5, 30 and 120 seconds
 * (Spec 4.3 item 4), then the notification is marked FAILED and measured. It never changes the order.
 * Contact data lives only for this attempt and is never logged.
 */
@Service
public class NotificationDispatchService implements DispatchNotificationsUseCase {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);
    private static final int[] RETRY_DELAYS_SECONDS = {5, 30, 120};

    private final NotificationRepositoryPort notifications;
    private final ContactLookupPort contacts;
    private final EmailSenderPort emails;
    private final MeterRegistry registry;

    public NotificationDispatchService(NotificationRepositoryPort notifications, ContactLookupPort contacts,
                                       EmailSenderPort emails, MeterRegistry registry) {
        this.notifications = notifications;
        this.contacts = contacts;
        this.emails = emails;
        this.registry = registry;
    }

    @Override
    public List<Notification> due(int limit) {
        return notifications.findDue(limit);
    }

    @Override
    public void dispatch(Notification notification) {
        try {
            CustomerContact contact = contacts.find(notification.customerId());
            emails.send(contact.email(), EmailTemplates.subject(notification.trigger()),
                    EmailTemplates.body(notification.trigger(), contact.displayName(), notification.orderId()));
            if (notifications.markSent(notification)) {
                count(notification, "SENT");
                log.info("Notification sent", kv("operation", "send notification"), kv("outcome", "SUCCESS"),
                        kv("trigger", notification.trigger()));
            }
        } catch (ContactNotFoundException e) {
            fail(notification, "CONTACT_NOT_FOUND");                       // permanent: retrying cannot help
        } catch (TransientNotificationException e) {
            int attempts = notification.attempts() + 1;
            if (attempts > RETRY_DELAYS_SECONDS.length) {
                fail(notification, "RETRIES_EXHAUSTED");
                return;
            }
            int delay = RETRY_DELAYS_SECONDS[attempts - 1];
            notifications.scheduleRetry(notification, attempts, Instant.now().plusSeconds(delay));
            log.warn("Notification attempt failed, retrying in {} s", delay, kv("operation", "send notification"),
                    kv("outcome", "RETRY"), kv("trigger", notification.trigger()),
                    kv("errorCode", e.getClass().getSimpleName()), kv("attempt", attempts));
        }
    }

    private void fail(Notification notification, String reason) {
        if (notifications.markFailed(notification)) {
            count(notification, "FAILED");
            log.error("Notification failed permanently; the order is not affected", kv("operation", "send notification"),
                    kv("outcome", "FAILURE"), kv("trigger", notification.trigger()), kv("errorCode", reason));
        }
    }

    private void count(Notification notification, String outcome) {
        // Prometheus: eatsnow_notifications_total{trigger, outcome} (Spec 6.4)
        registry.counter("eatsnow.notifications", "trigger", notification.trigger().name(), "outcome", outcome)
                .increment();
    }
}
