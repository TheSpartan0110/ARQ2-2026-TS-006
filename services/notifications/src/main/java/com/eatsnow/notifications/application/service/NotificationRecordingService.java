package com.eatsnow.notifications.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.notifications.domain.model.Notification;
import com.eatsnow.notifications.domain.model.NotificationStatus;
import com.eatsnow.notifications.domain.model.NotificationTrigger;
import com.eatsnow.notifications.domain.port.in.ProcessOrderStatusChangeUseCase;
import com.eatsnow.notifications.domain.port.out.NotificationRepositoryPort;
import com.eatsnow.notifications.domain.port.out.OrderVersionPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OrderStatusChanged consumer (Spec 4.2.2, 4.2.4). It only RECORDS the attempt as PENDING; the email is sent later by
 * the dispatcher, so a slow or failing provider can never hold a transaction or delay the broker acknowledgement.
 * Older versions of an order are acknowledged without sending, and the unique (orderId, trigger) stops duplicates.
 */
@Service
public class NotificationRecordingService implements ProcessOrderStatusChangeUseCase {
    private static final Logger log = LoggerFactory.getLogger(NotificationRecordingService.class);

    private final OrderVersionPort versions;
    private final NotificationRepositoryPort notifications;

    public NotificationRecordingService(OrderVersionPort versions, NotificationRepositoryPort notifications) {
        this.versions = versions;
        this.notifications = notifications;
    }

    @Override
    @Transactional
    public void process(Change change) {
        if (!versions.advance(change.orderId(), change.orderVersion())) {
            log.info("Older order version acknowledged without sending", kv("operation", "record notification"),
                    kv("outcome", "IGNORED"), kv("orderVersion", change.orderVersion()));
            return;
        }
        Optional<NotificationTrigger> trigger =
                NotificationTrigger.from(change.previousStatus(), change.status(), change.paymentStatus());
        if (trigger.isEmpty()) {
            return;                                       // acknowledged without sending (Spec 4.2.4)
        }
        Notification notification = new Notification(UUID.randomUUID(), change.orderId(), change.customerId(),
                trigger.get(), NotificationStatus.PENDING, 0, Instant.now(), change.correlationId(), 1);
        if (notifications.insertIfAbsent(notification)) {
            log.info("Notification recorded", kv("operation", "record notification"), kv("outcome", "SUCCESS"),
                    kv("trigger", trigger.get()));
        } else {
            log.info("Notification already recorded", kv("operation", "record notification"),
                    kv("outcome", "DUPLICATE"), kv("trigger", trigger.get()));
        }
    }
}
