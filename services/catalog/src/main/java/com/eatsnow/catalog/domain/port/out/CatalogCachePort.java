package com.eatsnow.catalog.domain.port.out;

import com.eatsnow.catalog.domain.model.MenuItem;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.catalog.domain.model.Restaurant;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache port representing Redis cache-aside operations (ADR 002).
 */
public interface CatalogCachePort {

    Optional<PageResult<Restaurant>> getRestaurants(int limit, String cursor);

    void putRestaurants(int limit, String cursor, PageResult<Restaurant> result, Duration ttl);

    Optional<PageResult<MenuItem>> getMenu(UUID restaurantId, int limit, String cursor);

    void putMenu(UUID restaurantId, int limit, String cursor, PageResult<MenuItem> result, Duration ttl);

    void evictRestaurant(UUID restaurantId);
}
