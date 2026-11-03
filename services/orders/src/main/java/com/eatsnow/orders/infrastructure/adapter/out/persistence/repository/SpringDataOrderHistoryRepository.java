package com.eatsnow.orders.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.orders.infrastructure.adapter.out.persistence.entity.OrderStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataOrderHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Long> {
}
