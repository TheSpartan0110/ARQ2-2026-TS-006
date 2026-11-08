package com.eatsnow.orders.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.orders.infrastructure.adapter.out.persistence.entity.OrderEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, UUID> {

    /** SELECT ... FOR UPDATE: serializes every state write of one order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") UUID id);

    /** First keyset page of a customer's orders, newest first (served by idx_orders_customer_id). */
    @Query("select o from OrderEntity o where o.customerId = :customerId order by o.createdAt desc, o.id desc")
    List<OrderEntity> findFirstPageByCustomer(@Param("customerId") UUID customerId, Pageable pageable);

    /** Next keyset page: orders strictly older than the (createdAt, id) cursor. */
    @Query("""
            select o from OrderEntity o
             where o.customerId = :customerId
               and (o.createdAt < :at or (o.createdAt = :at and o.id < :id))
             order by o.createdAt desc, o.id desc
            """)
    List<OrderEntity> findPageByCustomer(@Param("customerId") UUID customerId, @Param("at") Instant at,
                                         @Param("id") UUID id, Pageable pageable);
}
