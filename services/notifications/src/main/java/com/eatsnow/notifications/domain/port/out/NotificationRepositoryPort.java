package com.eatsnow.notifications.domain.port.out;

import com.eatsnow.notifications.domain.model.Notification;
import java.time.Instant;
import java.util.List;

public interface NotificationRepositoryPort {
    /** @return false if a notification already exists for (orderId, trigger) */
    boolean insertIfAbsent(Notification notification);

    /** PENDING notifications whose next attempt time has come, oldest first. */
    List<Notification> findDue(int limit);

    /** Conditional updates on version and PENDING status; false when someone else already changed it. */
    boolean markSent(Notification notification);

    boolean markFailed(Notification notification);

    boolean scheduleRetry(Notification notification, int attempts, Instant nextAttemptAt);
}
