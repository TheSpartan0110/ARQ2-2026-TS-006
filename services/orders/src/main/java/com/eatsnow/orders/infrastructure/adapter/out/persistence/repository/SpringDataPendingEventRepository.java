package com.eatsnow.orders.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.orders.infrastructure.adapter.out.persistence.entity.PendingEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataPendingEventRepository extends JpaRepository<PendingEventEntity, Long> {

    @Query("select p from PendingEventEntity p where p.orderId = :orderId and p.status = 'PENDING' order by p.id")
    List<PendingEventEntity> findPendingByOrder(@Param("orderId") UUID orderId);

    @Query("select distinct p.orderId from PendingEventEntity p where p.status = 'PENDING'")
    List<UUID> findOrderIdsWithPending(Pageable pageable);
}
