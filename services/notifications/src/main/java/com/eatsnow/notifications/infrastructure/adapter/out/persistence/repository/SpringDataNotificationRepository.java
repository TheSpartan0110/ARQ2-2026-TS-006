package com.eatsnow.notifications.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.notifications.infrastructure.adapter.out.persistence.entity.NotificationEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataNotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    @Query("select n from NotificationEntity n where n.status = 'PENDING' and n.nextAttemptAt <= :now order by n.nextAttemptAt")
    List<NotificationEntity> findDue(@Param("now") Instant now, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            update NotificationEntity n
               set n.status = 'SENT', n.attempts = n.attempts + 1, n.version = n.version + 1, n.updatedAt = :now
             where n.id = :id and n.version = :version and n.status = 'PENDING'
            """)
    int markSent(@Param("id") UUID id, @Param("version") int version, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update NotificationEntity n
               set n.status = 'FAILED', n.version = n.version + 1, n.updatedAt = :now
             where n.id = :id and n.version = :version and n.status = 'PENDING'
            """)
    int markFailed(@Param("id") UUID id, @Param("version") int version, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update NotificationEntity n
               set n.attempts = :attempts, n.nextAttemptAt = :next, n.version = n.version + 1, n.updatedAt = :now
             where n.id = :id and n.version = :version and n.status = 'PENDING'
            """)
    int scheduleRetry(@Param("id") UUID id, @Param("version") int version, @Param("attempts") int attempts,
                      @Param("next") Instant next, @Param("now") Instant now);
}
