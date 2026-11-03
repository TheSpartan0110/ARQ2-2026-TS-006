package com.eatsnow.delivery.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.delivery.infrastructure.adapter.out.persistence.entity.DeliveryEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataDeliveryRepository extends JpaRepository<DeliveryEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DeliveryEntity d where d.id = :id")
    Optional<DeliveryEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<DeliveryEntity> findByIdAndDriverId(UUID id, UUID driverId);

    @Query("select d.id from DeliveryEntity d where d.status = 'UNASSIGNED' order by d.createdAt, d.id")
    List<UUID> findUnassignedIds(Pageable pageable);

    @Query("""
            select d from DeliveryEntity d
             where d.driverId = :driverId
               and (d.createdAt > :at or (d.createdAt = :at and d.id > :id))
             order by d.createdAt, d.id
            """)
    List<DeliveryEntity> findPage(@Param("driverId") UUID driverId, @Param("at") Instant at, @Param("id") UUID id,
                                  Pageable pageable);

    @Query("""
            select d from DeliveryEntity d
             where d.driverId = :driverId and d.status = :status
               and (d.createdAt > :at or (d.createdAt = :at and d.id > :id))
             order by d.createdAt, d.id
            """)
    List<DeliveryEntity> findPageByStatus(@Param("driverId") UUID driverId, @Param("status") String status,
                                          @Param("at") Instant at, @Param("id") UUID id, Pageable pageable);

    /** WHERE id = ? AND version = expectedVersion. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update DeliveryEntity d
               set d.status = :status, d.driverId = :driverId, d.version = :newVersion, d.updatedAt = :now
             where d.id = :id and d.version = :expectedVersion
            """)
    int updateIfVersion(@Param("id") UUID id, @Param("expectedVersion") int expectedVersion,
                        @Param("status") String status, @Param("driverId") UUID driverId,
                        @Param("newVersion") int newVersion, @Param("now") Instant now);
}
