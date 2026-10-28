package com.eatsnow.catalog.domain.port.out;

import com.eatsnow.catalog.domain.model.MenuItem;
import com.eatsnow.common.domain.model.PageResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemRepositoryPort {
    Optional<MenuItem> findById(UUID id);
    PageResult<MenuItem> findByRestaurantId(UUID restaurantId, int limit, String cursor);
    List<MenuItem> findByIds(List<UUID> ids);
}
