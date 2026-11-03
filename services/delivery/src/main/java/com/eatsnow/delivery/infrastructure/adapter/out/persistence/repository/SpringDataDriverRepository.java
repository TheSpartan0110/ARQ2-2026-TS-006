package com.eatsnow.delivery.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.delivery.infrastructure.adapter.out.persistence.entity.DriverEntity;
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
public interface SpringDataDriverRepository extends JpaRepository<DriverEntity, UUID> {

    /** Longest time since the last assignment first; drivers never assigned come first (Spec 3.6). */
    @Query("""
            select d from DriverEntity d
             where d.onDuty = true and d.activeDeliveryId is null
             order by d.lastAssignedAt asc nulls first
            """)
    List<DriverEntity> findAssignable(Pageable pageable);

    /** WHERE id = ? AND onDuty = true AND activeDeliveryId IS NULL AND version = ? (Spec 3.6). */
    @Modifying(clearAutomatically = true)
    @Query("""
            update DriverEntity d
               set d.activeDeliveryId = :deliveryId, d.lastAssignedAt = :now, d.version = d.version + 1,
                   d.updatedAt = :now
             where d.id = :id and d.onDuty = true and d.activeDeliveryId is null and d.version = :version
            """)
    int claim(@Param("id") UUID id, @Param("version") int version, @Param("deliveryId") UUID deliveryId,
              @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update DriverEntity d
               set d.activeDeliveryId = null, d.version = d.version + 1, d.updatedAt = :now
             where d.id = :id and d.activeDeliveryId = :deliveryId
            """)
    int release(@Param("id") UUID id, @Param("deliveryId") UUID deliveryId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update DriverEntity d
               set d.onDuty = :onDuty, d.version = d.version + 1, d.updatedAt = :now
             where d.id = :id and d.version = :version
            """)
    int updateDuty(@Param("id") UUID id, @Param("version") int version, @Param("onDuty") boolean onDuty,
                   @Param("now") Instant now);
}
