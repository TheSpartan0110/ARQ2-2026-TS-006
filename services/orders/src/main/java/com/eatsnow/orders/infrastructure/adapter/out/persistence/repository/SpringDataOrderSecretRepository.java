package com.eatsnow.orders.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.orders.infrastructure.adapter.out.persistence.entity.OrderSecretEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataOrderSecretRepository extends JpaRepository<OrderSecretEntity, UUID> {
}
