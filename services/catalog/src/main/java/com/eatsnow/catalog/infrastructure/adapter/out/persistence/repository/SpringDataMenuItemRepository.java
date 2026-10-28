package com.eatsnow.catalog.infrastructure.adapter.out.persistence.repository;

import com.eatsnow.catalog.infrastructure.adapter.out.persistence.entity.MenuItemEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataMenuItemRepository extends JpaRepository<MenuItemEntity, UUID> {
    List<MenuItemEntity> findByRestaurantIdOrderByNameAsc(UUID restaurantId, Pageable pageable);
    List<MenuItemEntity> findByIdIn(List<UUID> ids);
}
