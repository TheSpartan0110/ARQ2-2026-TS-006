package com.eatsnow.catalog.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.catalog.infrastructure.adapter.out.persistence.entity.RestaurantEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataRestaurantRepository extends JpaRepository<RestaurantEntity, UUID> {
    List<RestaurantEntity> findByOrderByNameAsc(Pageable pageable);
}
