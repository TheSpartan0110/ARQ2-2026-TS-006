package com.eatsnow.notifications.domain.port.in;

import com.eatsnow.notifications.domain.model.Notification;
import java.util.List;

/** Sends the recorded notifications outside any database transaction (ADR 006). Driven by the scheduler adapter. */
public interface DispatchNotificationsUseCase {
    List<Notification> due(int limit);

    /** Resolves the contact, sends the email and records SENT; on failure schedules a retry or marks FAILED. */
    void dispatch(Notification notification);
}
