package com.eatsnow.catalog;

import com.eatsnow.catalog.application.service.CatalogService;
import com.eatsnow.catalog.application.service.QuoteService;
import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.catalog.domain.model.MenuItem;
import com.eatsnow.common.domain.model.Money;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.catalog.domain.model.Quote;
import com.eatsnow.catalog.domain.model.RequestedItem;
import com.eatsnow.catalog.domain.model.Restaurant;
import com.eatsnow.catalog.domain.port.out.CatalogCachePort;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.catalog.domain.port.out.MenuItemRepositoryPort;
import com.eatsnow.catalog.domain.port.out.QuoteSignerPort;
import com.eatsnow.catalog.domain.port.out.RestaurantRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
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
    @Mock
    private QuoteSignerPort quoteSigner;
    @Mock
    private IdempotencyStoragePort idempotencyStorage;

    private CatalogService catalogService;
    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(restaurantRepository, menuItemRepository, catalogCache);
        quoteService = new QuoteService(restaurantRepository, menuItemRepository, quoteSigner, idempotencyStorage);
    }

    @Test
    void shouldReturnRestaurantsFromCacheIfPresent() {
        UUID restaurantId = UUID.randomUUID();
        PageResult<Restaurant> cachedResult = new PageResult<>(
                List.of(new Restaurant(restaurantId, "Burger House", true)),
                null
        );
        when(catalogCache.getRestaurants(20, null)).thenReturn(Optional.of(cachedResult));

        PageResult<Restaurant> result = catalogService.getRestaurants(20, null);

        assertEquals(1, result.items().size());
        assertEquals("Burger House", result.items().get(0).name());
        verify(restaurantRepository, never()).findAll(anyInt(), any());
    }

    @Test
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
        verify(catalogCache).putRestaurants(eq(20), isNull(), eq(dbResult), any(Duration.class));
    }

    @Test
    void shouldRejectQuoteWithDuplicateItems() {
        UUID customerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        List<RequestedItem> duplicateItems = List.of(
                new RequestedItem(itemId, 1),
                new RequestedItem(itemId, 2)
        );

        assertThrows(InvalidRequestException.class, () ->
                quoteService.createQuote(customerId, restaurantId, duplicateItems, null, "{}")
        );
    }

    @Test
    void shouldCreateAndSignQuoteSuccessfully() {
        UUID customerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        Restaurant restaurant = new Restaurant(restaurantId, "Burger House", true);
        MenuItem menuItem = new MenuItem(itemId, restaurantId, "Classic Burger", Money.cop(2500000), true, 1);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByIds(List.of(itemId))).thenReturn(List.of(menuItem));
        when(quoteSigner.signQuoteToken(any(), eq(customerId), eq(restaurantId), any(), any(), any()))
                .thenReturn("mock-signed-jwt-token");

        List<RequestedItem> items = List.of(new RequestedItem(itemId, 2));

        Quote quote = quoteService.createQuote(customerId, restaurantId, items, null, "{}");

        assertNotNull(quote);
        assertEquals(restaurantId, quote.restaurantId());
        assertEquals(5000000L, quote.total().amountMinor());
        assertEquals("mock-signed-jwt-token", quote.quoteToken());
    }
}
