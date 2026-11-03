package com.eatsnow.payments.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.payments.infrastructure.adapter.out.persistence.entity.RefundEntity;
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
public interface SpringDataRefundRepository extends JpaRepository<RefundEntity, UUID> {

    @Query("select r from RefundEntity r where r.status = :status order by r.createdAt")
    List<RefundEntity> findByStatusOrdered(@Param("status") String status, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            update RefundEntity r
               set r.status = 'REFUNDED', r.providerReference = :reference, r.version = r.version + 1, r.updatedAt = :now
             where r.id = :id and r.version = :version and r.status in ('PENDING', 'UNKNOWN')
            """)
    int markRefunded(@Param("id") UUID id, @Param("version") int version, @Param("reference") String reference,
                     @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update RefundEntity r
               set r.status = 'FAILED', r.failureCode = :code, r.version = r.version + 1, r.updatedAt = :now
             where r.id = :id and r.version = :version and r.status in ('PENDING', 'UNKNOWN')
            """)
    int markFailed(@Param("id") UUID id, @Param("version") int version, @Param("code") String code,
                   @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update RefundEntity r
               set r.status = 'UNKNOWN', r.version = r.version + 1, r.updatedAt = :now
             where r.id = :id and r.version = :version and r.status = 'PENDING'
            """)
    int markUnknown(@Param("id") UUID id, @Param("version") int version, @Param("now") Instant now);
}
