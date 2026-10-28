package com.eatsnow.catalog.application.service;

import com.eatsnow.catalog.domain.model.MenuItem;
import com.eatsnow.catalog.domain.model.Restaurant;
import com.eatsnow.catalog.domain.port.out.CatalogCachePort;
import com.eatsnow.catalog.domain.port.out.MenuItemRepositoryPort;
import com.eatsnow.catalog.domain.port.out.RestaurantRepositoryPort;
import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.model.Money;
import com.eatsnow.common.domain.model.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private RestaurantRepositoryPort restaurantRepository;
    @Mock
    private MenuItemRepositoryPort menuItemRepository;
    @Mock
    private CatalogCachePort catalogCache;

    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(restaurantRepository, menuItemRepository, catalogCache);
    }

    @Test
    @DisplayName("Should return restaurants directly from Redis cache on cache hit (ADR 002)")
    void shouldReturnRestaurantsFromCacheOnHit() {
        UUID restaurantId = UUID.randomUUID();
        PageResult<Restaurant> cachedResult = new PageResult<>(
                List.of(new Restaurant(restaurantId, "Burger House", true)),
                null
        );
        when(catalogCache.getRestaurants(20, null)).thenReturn(Optional.of(cachedResult));

        PageResult<Restaurant> result = catalogService.getRestaurants(20, null);

        assertNotNull(result);
        assertEquals(1, result.items().size());
        assertEquals("Burger House", result.items().get(0).name());
        verify(restaurantRepository, never()).findAll(anyInt(), any());
    }

    @Test
    @DisplayName("Should query PostgreSQL and populate Redis cache on cache miss (ADR 002)")
    void shouldQueryDbAndPopulateCacheOnMiss() {
        UUID restaurantId = UUID.randomUUID();
        PageResult<Restaurant> dbResult = new PageResult<>(
                List.of(new Restaurant(restaurantId, "Burger House", true)),
                null
        );
        when(catalogCache.getRestaurants(20, null)).thenReturn(Optional.empty());
        when(restaurantRepository.findAll(20, null)).thenReturn(dbResult);

        PageResult<Restaurant> result = catalogService.getRestaurants(20, null);

        assertEquals(1, result.items().size());
        verify(restaurantRepository, times(1)).findAll(20, null);
        verify(catalogCache, times(1)).putRestaurants(eq(20), isNull(), eq(dbResult), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("Should bound pagination limit between 1 and 100 (Spec 3.1)")
    void shouldBoundPaginationLimits() {
        when(catalogCache.getRestaurants(anyInt(), any())).thenReturn(Optional.of(new PageResult<>(List.of(), null)));

        // Test limit over 100 gets bounded to 100
        catalogService.getRestaurants(150, null);
        verify(catalogCache).getRestaurants(100, null);

        // Test limit <= 0 defaults to 20
        catalogService.getRestaurants(0, null);
        verify(catalogCache).getRestaurants(20, null);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException if restaurant does not exist when fetching menu")
    void shouldThrowWhenRestaurantNotFoundForMenu() {
        UUID nonExistentId = UUID.randomUUID();
        when(restaurantRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () ->
                catalogService.getMenu(nonExistentId, 20, null)
        );

        assertTrue(ex.getMessage().contains(nonExistentId.toString()));
        assertEquals(404, ex.getHttpStatus());
        assertEquals("NOT_FOUND", ex.getCode());
        verify(menuItemRepository, never()).findByRestaurantId(any(), anyInt(), any());
    }

    @Test
    @DisplayName("Should return menu from Redis on hit and skip database")
    void shouldReturnMenuFromCacheOnHit() {
        UUID restaurantId = UUID.randomUUID();
        Restaurant restaurant = new Restaurant(restaurantId, "Burger House", true);
        MenuItem item = new MenuItem(UUID.randomUUID(), restaurantId, "Classic Burger", Money.cop(2500000), true, 1);
        PageResult<MenuItem> cachedMenu = new PageResult<>(List.of(item), null);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(catalogCache.getMenu(restaurantId, 20, null)).thenReturn(Optional.of(cachedMenu));

        PageResult<MenuItem> result = catalogService.getMenu(restaurantId, 20, null);

        assertEquals(1, result.items().size());
        assertEquals("Classic Burger", result.items().get(0).name());
        verify(menuItemRepository, never()).findByRestaurantId(any(), anyInt(), any());
    }

    @Test
    @DisplayName("Should query PostgreSQL on menu cache miss and populate cache with 60s TTL")
    void shouldQueryDbForMenuOnMiss() {
        UUID restaurantId = UUID.randomUUID();
        Restaurant restaurant = new Restaurant(restaurantId, "Burger House", true);
        MenuItem item = new MenuItem(UUID.randomUUID(), restaurantId, "Classic Burger", Money.cop(2500000), true, 1);
        PageResult<MenuItem> dbMenu = new PageResult<>(List.of(item), null);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(catalogCache.getMenu(restaurantId, 20, null)).thenReturn(Optional.empty());
        when(menuItemRepository.findByRestaurantId(restaurantId, 20, null)).thenReturn(dbMenu);

        PageResult<MenuItem> result = catalogService.getMenu(restaurantId, 20, null);

        assertEquals(1, result.items().size());
        verify(menuItemRepository).findByRestaurantId(restaurantId, 20, null);
        verify(catalogCache).putMenu(eq(restaurantId), eq(20), isNull(), eq(dbMenu), eq(Duration.ofSeconds(60)));
    }
}
