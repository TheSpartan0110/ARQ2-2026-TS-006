package com.eatsnow.catalog.infrastructure.adapter.out.cache;

import com.eatsnow.catalog.domain.model.MenuItem;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.catalog.domain.model.Restaurant;
import com.eatsnow.catalog.domain.port.out.CatalogCachePort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis cache adapter implementing Cache-Aside pattern (ADR 002).
 * Metrics: eatsnow_catalog_cache_requests_total with tag result="hit|miss|error" (Spec 6.4).
 * Cache failures fail open to allow fallback queries directly to PostgreSQL.
 */
@Component
public class RedisCatalogCacheAdapter implements CatalogCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisCatalogCacheAdapter.class);
    private static final String METRIC_NAME = "eatsnow_catalog_cache_requests_total";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public RedisCatalogCacheAdapter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Optional<PageResult<Restaurant>> getRestaurants(int limit, String cursor) {
        String key = "catalog:restaurants:limit=" + limit + ":cursor=" + (cursor != null ? cursor : "0");
        return readFromCache(key, new TypeReference<PageResult<Restaurant>>() {});
    }

    @Override
    public void putRestaurants(int limit, String cursor, PageResult<Restaurant> result, Duration ttl) {
        String key = "catalog:restaurants:limit=" + limit + ":cursor=" + (cursor != null ? cursor : "0");
        writeToCache(key, result, ttl);
    }

    @Override
    public Optional<PageResult<MenuItem>> getMenu(UUID restaurantId, int limit, String cursor) {
        String key = "catalog:menu:" + restaurantId + ":limit=" + limit + ":cursor=" + (cursor != null ? cursor : "0");
        return readFromCache(key, new TypeReference<PageResult<MenuItem>>() {});
    }

    @Override
    public void putMenu(UUID restaurantId, int limit, String cursor, PageResult<MenuItem> result, Duration ttl) {
        String key = "catalog:menu:" + restaurantId + ":limit=" + limit + ":cursor=" + (cursor != null ? cursor : "0");
        writeToCache(key, result, ttl);
    }

    @Override
    public void evictRestaurant(UUID restaurantId) {
        try {
            Set<String> keys = redisTemplate.keys("catalog:menu:" + restaurantId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            Set<String> restaurantKeys = redisTemplate.keys("catalog:restaurants:*");
            if (restaurantKeys != null && !restaurantKeys.isEmpty()) {
                redisTemplate.delete(restaurantKeys);
            }
        } catch (Exception ex) {
            log.warn("Redis error during cache eviction for restaurant {}: {}", restaurantId, ex.getMessage());
            recordMetric("error");
        }
    }

    private <T> Optional<T> readFromCache(String key, TypeReference<T> typeRef) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                recordMetric("hit");
                T value = objectMapper.readValue(json, typeRef);
                return Optional.of(value);
            } else {
                recordMetric("miss");
                return Optional.empty();
            }
        } catch (Exception ex) {
            log.warn("Redis error reading key {}: {}. Falling back to PostgreSQL.", key, ex.getMessage());
            recordMetric("error");
            return Optional.empty();
        }
    }

    private void writeToCache(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception ex) {
            log.warn("Redis error writing key {}: {}", key, ex.getMessage());
            recordMetric("error");
        }
    }

    private void recordMetric(String result) {
        try {
            meterRegistry.counter(METRIC_NAME, "result", result).increment();
        } catch (Exception e) {
            log.debug("Error recording metric: {}", e.getMessage());
        }
    }
}
