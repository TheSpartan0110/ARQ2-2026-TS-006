package com.eatsnow.catalog.application.service;

import com.eatsnow.catalog.domain.model.MenuItem;
import com.eatsnow.catalog.domain.model.Quote;
import com.eatsnow.catalog.domain.model.RequestedItem;
import com.eatsnow.catalog.domain.model.Restaurant;
import com.eatsnow.catalog.domain.port.out.MenuItemRepositoryPort;
import com.eatsnow.catalog.domain.port.out.QuoteSignerPort;
import com.eatsnow.catalog.domain.port.out.RestaurantRepositoryPort;
import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.IdempotencyConflictException;
import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.common.domain.exception.ItemUnavailableException;
import com.eatsnow.common.domain.exception.RequestInProgressException;
import com.eatsnow.common.domain.model.IdempotencyEntry;
import com.eatsnow.common.domain.model.Money;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private RestaurantRepositoryPort restaurantRepository;
    @Mock
    private MenuItemRepositoryPort menuItemRepository;
    @Mock
    private QuoteSignerPort quoteSigner;
    @Mock
    private IdempotencyStoragePort idempotencyStorage;

    private QuoteService quoteService;

    private final UUID customerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID itemId1 = UUID.randomUUID();
    private final UUID itemId2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        quoteService = new QuoteService(restaurantRepository, menuItemRepository, quoteSigner, idempotencyStorage);
    }

    @Test
    @DisplayName("Should create and sign quote successfully calculating total in COP minor units (Spec 2.2 & 2.3)")
    void shouldCreateAndSignQuoteSuccessfully() {
        Restaurant restaurant = new Restaurant(restaurantId, "Burger House", true);
        MenuItem item1 = new MenuItem(itemId1, restaurantId, "Classic Burger", Money.cop(2500000), true, 1);
        MenuItem item2 = new MenuItem(itemId2, restaurantId, "Cheese Fries", Money.cop(1200000), true, 1);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByIds(List.of(itemId1, itemId2))).thenReturn(List.of(item1, item2));
        when(quoteSigner.signQuoteToken(any(), eq(customerId), eq(restaurantId), any(), any(), any()))
                .thenReturn("signed-rs256-jwt-token");

        List<RequestedItem> items = List.of(
                new RequestedItem(itemId1, 2), // 2 * 25,000 = 50,000 (5,000,000 minor)
                new RequestedItem(itemId2, 1)  // 1 * 12,000 = 12,000 (1,200,000 minor)
        );

        Quote quote = quoteService.createQuote(customerId, restaurantId, items, "key-1", "{\"raw\":\"payload\"}");

        assertNotNull(quote);
        assertEquals(customerId, quote.customerId());
        assertEquals(restaurantId, quote.restaurantId());
        assertEquals(2, quote.items().size());
        assertEquals(6200000L, quote.total().amountMinor());
        assertEquals("COP", quote.total().currency());
        assertEquals("signed-rs256-jwt-token", quote.quoteToken());
        assertTrue(quote.expiresAt().isAfter(Instant.now().plusSeconds(110)));
        verify(idempotencyStorage).start(anyString(), anyString());
        verify(idempotencyStorage).complete(anyString(), anyString(), eq(201), anyString());
    }

    @Test
    @DisplayName("Should reject quote if requested items contain duplicate menuItemId (Spec 2.2)")
    void shouldRejectDuplicateMenuItemIds() {
        List<RequestedItem> duplicateItems = List.of(
                new RequestedItem(itemId1, 1),
                new RequestedItem(itemId1, 2)
        );

        InvalidRequestException ex = assertThrows(InvalidRequestException.class, () ->
                quoteService.createQuote(customerId, restaurantId, duplicateItems, null, "{}")
        );

        assertTrue(ex.getMessage().contains("Duplicate menuItemId"));
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    @DisplayName("Should reject quote if items list is empty or exceeds 50 items (Spec 2.2)")
    void shouldRejectInvalidItemCount() {
        assertThrows(InvalidRequestException.class, () ->
                quoteService.createQuote(customerId, restaurantId, List.of(), null, "{}")
        );

        List<RequestedItem> tooManyItems = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            tooManyItems.add(new RequestedItem(UUID.randomUUID(), 1));
        }

        assertThrows(InvalidRequestException.class, () ->
                quoteService.createQuote(customerId, restaurantId, tooManyItems, null, "{}")
        );
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException if restaurant does not exist in PostgreSQL")
    void shouldThrowIfRestaurantNotFound() {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
                quoteService.createQuote(customerId, restaurantId, List.of(new RequestedItem(itemId1, 1)), null, "{}")
        );
    }

    @Test
    @DisplayName("Should throw ItemUnavailableException if restaurant is currently unavailable")
    void shouldThrowIfRestaurantUnavailable() {
        Restaurant unavailableRestaurant = new Restaurant(restaurantId, "Burger House", false);
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(unavailableRestaurant));

        ItemUnavailableException ex = assertThrows(ItemUnavailableException.class, () ->
                quoteService.createQuote(customerId, restaurantId, List.of(new RequestedItem(itemId1, 1)), null, "{}")
        );

        assertEquals(409, ex.getHttpStatus());
        assertEquals("ITEM_UNAVAILABLE", ex.getCode());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException if a requested menu item is not found in PostgreSQL")
    void shouldThrowIfMenuItemNotFound() {
        Restaurant restaurant = new Restaurant(restaurantId, "Burger House", true);
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByIds(List.of(itemId1))).thenReturn(List.of()); // Not found

        assertThrows(EntityNotFoundException.class, () ->
                quoteService.createQuote(customerId, restaurantId, List.of(new RequestedItem(itemId1, 1)), null, "{}")
        );
    }

    @Test
    @DisplayName("Should throw InvalidRequestException if an item belongs to a different restaurant")
    void shouldThrowIfItemBelongsToDifferentRestaurant() {
        UUID otherRestaurantId = UUID.randomUUID();
        Restaurant restaurant = new Restaurant(restaurantId, "Burger House", true);
        MenuItem alienItem = new MenuItem(itemId1, otherRestaurantId, "Classic Burger", Money.cop(2500000), true, 1);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByIds(List.of(itemId1))).thenReturn(List.of(alienItem));

        InvalidRequestException ex = assertThrows(InvalidRequestException.class, () ->
                quoteService.createQuote(customerId, restaurantId, List.of(new RequestedItem(itemId1, 1)), null, "{}")
        );

        assertTrue(ex.getMessage().contains("does not belong to specified restaurant"));
    }

    @Test
    @DisplayName("Should throw ItemUnavailableException if an item is not available (available = false)")
    void shouldThrowIfItemUnavailable() {
        Restaurant restaurant = new Restaurant(restaurantId, "Burger House", true);
        MenuItem soldOutItem = new MenuItem(itemId1, restaurantId, "Classic Burger", Money.cop(2500000), false, 1);

        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findByIds(List.of(itemId1))).thenReturn(List.of(soldOutItem));

        ItemUnavailableException ex = assertThrows(ItemUnavailableException.class, () ->
                quoteService.createQuote(customerId, restaurantId, List.of(new RequestedItem(itemId1, 1)), null, "{}")
        );

        assertEquals(409, ex.getHttpStatus());
        assertEquals("ITEM_UNAVAILABLE", ex.getCode());
    }

    @Test
    @DisplayName("Should throw RequestInProgressException if concurrent request with same Idempotency-Key is running (Spec 3.1)")
    void shouldThrowIfRequestInProgress() {
        IdempotencyEntry inProgressEntry = new IdempotencyEntry(
                "scope-1",
                "hash-1",
                "IN_PROGRESS",
                0,
                null,
                Instant.now()
        );
        when(idempotencyStorage.find(anyString())).thenReturn(Optional.of(inProgressEntry));

        RequestInProgressException ex = assertThrows(RequestInProgressException.class, () ->
                quoteService.createQuote(customerId, restaurantId, List.of(new RequestedItem(itemId1, 1)), "key-1", "{}")
        );

        assertEquals(409, ex.getHttpStatus());
        assertEquals("REQUEST_IN_PROGRESS", ex.getCode());
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException if same Idempotency-Key is reused with different payload (Spec 3.1)")
    void shouldThrowIfIdempotencyConflict() {
        IdempotencyEntry completedEntry = new IdempotencyEntry(
                "scope-1",
                "different-hash",
                "COMPLETED",
                201,
                "{}",
                Instant.now()
        );
        when(idempotencyStorage.find(anyString())).thenReturn(Optional.of(completedEntry));

        IdempotencyConflictException ex = assertThrows(IdempotencyConflictException.class, () ->
                quoteService.createQuote(customerId, restaurantId, List.of(new RequestedItem(itemId1, 1)), "key-1", "{\"new\":\"payload\"}")
        );

        assertEquals(409, ex.getHttpStatus());
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
    }
}
