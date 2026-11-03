package com.eatsnow.notifications.infrastructure.adapter.in.scheduler;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.messaging.support.LogContext;
import com.eatsnow.notifications.domain.model.Notification;
import com.eatsnow.notifications.domain.port.in.DispatchNotificationsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Drives the email dispatcher on a timer, outside the broker consumer and outside any transaction (ADR 006). */
@Component
public class NotificationDispatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchScheduler.class);

    private final DispatchNotificationsUseCase dispatcher;

    public NotificationDispatchScheduler(DispatchNotificationsUseCase dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${eatsnow.notifications.dispatch-interval-ms:1000}")
    public void dispatchDue() {
        try {
            for (Notification notification : dispatcher.due(20)) {
                try {
                    LogContext.workflow(notification.orderId(), notification.correlationId());
                    dispatcher.dispatch(notification);
                } catch (RuntimeException e) {
                    log.warn("Notification dispatch failed unexpectedly", kv("operation", "send notification"),
                            kv("outcome", "RETRY"), e);
                } finally {
                    LogContext.clear();
                }
            }
        } catch (RuntimeException e) {
            log.warn("Notification polling failed", kv("operation", "send notification"), kv("outcome", "FAILURE"), e);
        }
    }
}
