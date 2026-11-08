package com.eatsnow.orders.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.orders.infrastructure.adapter.out.persistence.entity.OrderStatusHistoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataOrderHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Long> {

    /** Oldest first; the id breaks ties between changes recorded in the same instant. */
    List<OrderStatusHistoryEntity> findByOrderIdOrderByOccurredAtAscIdAsc(UUID orderId);
}
