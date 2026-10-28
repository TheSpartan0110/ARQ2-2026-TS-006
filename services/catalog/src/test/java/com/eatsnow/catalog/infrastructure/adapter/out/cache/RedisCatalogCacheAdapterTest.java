package com.eatsnow.catalog.infrastructure.adapter.out.cache;

import com.eatsnow.catalog.domain.model.Restaurant;
import com.eatsnow.common.domain.model.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisCatalogCacheAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private Counter counter;

    private ObjectMapper objectMapper;
    private RedisCatalogCacheAdapter cacheAdapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        cacheAdapter = new RedisCatalogCacheAdapter(redisTemplate, objectMapper, meterRegistry);
    }

    @Test
    @DisplayName("Should return cached restaurants and increment hit metric on cache hit (Spec 6.4)")
    void shouldReturnCachedRestaurantsAndRecordHitMetric() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID restaurantId = UUID.randomUUID();
        String json = "{\"items\":[{\"id\":\"" + restaurantId + "\",\"name\":\"Burger House\",\"available\":true}],\"nextCursor\":null}";
        when(valueOperations.get(anyString())).thenReturn(json);

        Optional<PageResult<Restaurant>> result = cacheAdapter.getRestaurants(20, null);

        assertTrue(result.isPresent());
        assertEquals("Burger House", result.get().items().get(0).name());
        verify(meterRegistry).counter("eatsnow_catalog_cache_requests_total", "result", "hit");
        verify(counter).increment();
    }

    @Test
    @DisplayName("Should return empty and increment miss metric on cache miss (Spec 6.4)")
    void shouldReturnEmptyAndRecordMissMetricOnMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        Optional<PageResult<Restaurant>> result = cacheAdapter.getRestaurants(20, null);

        assertTrue(result.isEmpty());
        verify(meterRegistry).counter("eatsnow_catalog_cache_requests_total", "result", "miss");
        verify(counter).increment();
    }

    @Test
    @DisplayName("Should fail open to empty optional and increment error metric when Redis throws exception (ADR 002)")
    void shouldFailOpenAndRecordErrorMetricWhenRedisFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("Redis connection refused"));

        // Must not propagate exception, allowing graceful fallback to PostgreSQL
        Optional<PageResult<Restaurant>> result = cacheAdapter.getRestaurants(20, null);

        assertTrue(result.isEmpty());
        verify(meterRegistry).counter("eatsnow_catalog_cache_requests_total", "result", "error");
        verify(counter).increment();
    }

    @Test
    @DisplayName("Should write to Redis with specified TTL without throwing")
    void shouldWriteToRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        PageResult<Restaurant> page = new PageResult<>(List.of(new Restaurant(UUID.randomUUID(), "Test", true)), null);

        assertDoesNotThrow(() ->
                cacheAdapter.putRestaurants(20, null, page, Duration.ofSeconds(60))
        );

        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(60)));
    }
}
