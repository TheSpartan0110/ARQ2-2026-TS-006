package com.eatsnow.catalog.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.catalog.infrastructure.adapter.out.persistence.entity.IdempotencyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataIdempotencyRepository extends JpaRepository<IdempotencyRecordEntity, String> {
}
