package com.eatsnow.catalog.application.service;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.catalog.domain.model.MenuItem;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.catalog.domain.model.Restaurant;
import com.eatsnow.catalog.domain.port.in.GetRestaurantMenuQuery;
import com.eatsnow.catalog.domain.port.in.GetRestaurantsQuery;
import com.eatsnow.catalog.domain.port.out.CatalogCachePort;
import com.eatsnow.catalog.domain.port.out.MenuItemRepositoryPort;
import com.eatsnow.catalog.domain.port.out.RestaurantRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Catalog application service implementing Cache-Aside pattern (ADR 002).
 * Reads check Redis first; on miss, query PostgreSQL and populate cache with 60s TTL.
 * Cache failures fail open to PostgreSQL to ensure high availability.
 */
@Service
public class CatalogService implements GetRestaurantsQuery, GetRestaurantMenuQuery {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(60); // Spec 3.3

    private final RestaurantRepositoryPort restaurantRepository;
    private final MenuItemRepositoryPort menuItemRepository;
    private final CatalogCachePort catalogCache;

    public CatalogService(
            RestaurantRepositoryPort restaurantRepository,
            MenuItemRepositoryPort menuItemRepository,
            CatalogCachePort catalogCache
    ) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.catalogCache = catalogCache;
    }

    @Override
    public PageResult<Restaurant> getRestaurants(int limit, String cursor) {
        int boundedLimit = boundLimit(limit);

        // 1. Try cache-aside read
        Optional<PageResult<Restaurant>> cached = catalogCache.getRestaurants(boundedLimit, cursor);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. Fallback to PostgreSQL
        PageResult<Restaurant> fromDb = restaurantRepository.findAll(boundedLimit, cursor);

        // 3. Populate cache
        catalogCache.putRestaurants(boundedLimit, cursor, fromDb, CACHE_TTL);

        return fromDb;
    }

    @Override
    public PageResult<MenuItem> getMenu(UUID restaurantId, int limit, String cursor) {
        int boundedLimit = boundLimit(limit);

        // Check restaurant existence first
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant not found: " + restaurantId));

        // 1. Try cache-aside read
        Optional<PageResult<MenuItem>> cached = catalogCache.getMenu(restaurantId, boundedLimit, cursor);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. Fallback to PostgreSQL
        PageResult<MenuItem> fromDb = menuItemRepository.findByRestaurantId(restaurantId, boundedLimit, cursor);

        // 3. Populate cache
        catalogCache.putMenu(restaurantId, boundedLimit, cursor, fromDb, CACHE_TTL);

        return fromDb;
    }

    private int boundLimit(int limit) {
        if (limit <= 0) return 20;
        return Math.min(limit, 100);
    }
}
