package com.eatsnow.payments.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.payments.infrastructure.adapter.out.persistence.entity.PaymentEntity;
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
public interface SpringDataPaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    @Query("select p from PaymentEntity p where p.status = :status order by p.createdAt")
    List<PaymentEntity> findByStatusOrdered(@Param("status") String status, Pageable pageable);

    /** Conditional on version and an open status: at most one definitive outcome is ever applied. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentEntity p
               set p.status = 'AUTHORIZED', p.providerReference = :reference, p.encryptedPaymentToken = null,
                   p.pendingSince = null, p.version = p.version + 1, p.updatedAt = :now
             where p.id = :id and p.version = :version and p.status in ('PENDING', 'UNKNOWN')
            """)
    int markAuthorized(@Param("id") UUID id, @Param("version") int version, @Param("reference") String reference,
                       @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentEntity p
               set p.status = 'FAILED', p.failureCode = :code, p.encryptedPaymentToken = null,
                   p.pendingSince = null, p.version = p.version + 1, p.updatedAt = :now
             where p.id = :id and p.version = :version and p.status in ('PENDING', 'UNKNOWN')
            """)
    int markFailed(@Param("id") UUID id, @Param("version") int version, @Param("code") String code,
                   @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentEntity p
               set p.status = 'UNKNOWN', p.pendingSince = :now, p.version = p.version + 1,
                   p.updatedAt = :now
             where p.id = :id and p.version = :version and p.status = 'PENDING'
            """)
    int markUnknown(@Param("id") UUID id, @Param("version") int version, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentEntity p
               set p.status = 'REFUND_PENDING', p.version = p.version + 1, p.updatedAt = :now
             where p.id = :id and p.version = :version and p.status = 'AUTHORIZED'
            """)
    int markRefundPending(@Param("id") UUID id, @Param("version") int version, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentEntity p
               set p.status = 'REFUNDED', p.version = p.version + 1, p.updatedAt = :now
             where p.id = :id and p.version = :version and p.status = 'REFUND_PENDING'
            """)
    int markRefunded(@Param("id") UUID id, @Param("version") int version, @Param("now") Instant now);
}
