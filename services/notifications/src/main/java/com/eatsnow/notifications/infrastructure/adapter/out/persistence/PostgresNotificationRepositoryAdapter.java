package com.eatsnow.notifications.infrastructure.adapter.out.persistence;

import com.eatsnow.notifications.domain.model.Notification;
import com.eatsnow.notifications.domain.model.NotificationStatus;
import com.eatsnow.notifications.domain.model.NotificationTrigger;
import com.eatsnow.notifications.domain.port.out.NotificationRepositoryPort;
import com.eatsnow.notifications.infrastructure.adapter.out.persistence.entity.NotificationEntity;
import com.eatsnow.notifications.infrastructure.adapter.out.persistence.repository.SpringDataNotificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresNotificationRepositoryAdapter implements NotificationRepositoryPort {

    private final SpringDataNotificationRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresNotificationRepositoryAdapter(SpringDataNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean insertIfAbsent(Notification n) {
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO notifications.notifications (id, order_id, customer_id, trigger_name, channel, status,
                                                         attempts, next_attempt_at, correlation_id, version)
                VALUES (:id, :orderId, :customerId, :trigger, 'EMAIL', 'PENDING', 0, now(), :correlationId, 1)
                ON CONFLICT (order_id, trigger_name) DO NOTHING
                """)
                .setParameter("id", n.id())
                .setParameter("orderId", n.orderId())
                .setParameter("customerId", n.customerId())
                .setParameter("trigger", n.trigger().name())
                .setParameter("correlationId", n.correlationId())
                .executeUpdate();
        return inserted == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findDue(int limit) {
        return repository.findDue(Instant.now(), PageRequest.of(0, limit)).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean markSent(Notification n) {
        return repository.markSent(n.id(), n.version(), Instant.now()) == 1;
    }

    @Override
    @Transactional
    public boolean markFailed(Notification n) {
        return repository.markFailed(n.id(), n.version(), Instant.now()) == 1;
    }

    @Override
    @Transactional
    public boolean scheduleRetry(Notification n, int attempts, Instant nextAttemptAt) {
        return repository.scheduleRetry(n.id(), n.version(), attempts, nextAttemptAt, Instant.now()) == 1;
    }

    private Notification toDomain(NotificationEntity e) {
        return new Notification(e.getId(), e.getOrderId(), e.getCustomerId(),
                NotificationTrigger.valueOf(e.getTriggerName()), NotificationStatus.valueOf(e.getStatus()),
                e.getAttempts(), e.getNextAttemptAt(), e.getCorrelationId(), e.getVersion());
    }
}
