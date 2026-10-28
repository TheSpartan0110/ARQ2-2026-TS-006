package com.eatsnow.catalog.infrastructure.adapter.in.web;

import com.eatsnow.catalog.domain.model.MenuItem;
import com.eatsnow.catalog.domain.model.OrderItem;
import com.eatsnow.catalog.domain.model.Quote;
import com.eatsnow.catalog.domain.model.Restaurant;
import com.eatsnow.catalog.domain.port.in.CreateQuoteUseCase;
import com.eatsnow.catalog.domain.port.in.GetRestaurantMenuQuery;
import com.eatsnow.catalog.domain.port.in.GetRestaurantsQuery;
import com.eatsnow.catalog.domain.port.out.QuoteSignerPort;
import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.IdempotencyConflictException;
import com.eatsnow.common.domain.exception.ItemUnavailableException;
import com.eatsnow.common.domain.exception.RequestInProgressException;
import com.eatsnow.common.domain.model.Money;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.common.web.GlobalExceptionHandler;
import com.eatsnow.common.web.filter.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CatalogController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
class CatalogControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GetRestaurantsQuery getRestaurantsQuery;

    @MockBean
    private GetRestaurantMenuQuery getRestaurantMenuQuery;

    @MockBean
    private CreateQuoteUseCase createQuoteUseCase;

    @MockBean
    private QuoteSignerPort quoteSignerPort;

    private final UUID restaurantId = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private final UUID menuItemId = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private final UUID correlationId = UUID.fromString("00000000-0000-4000-8000-000000000040");
    private final UUID idempotencyKey = UUID.fromString("00000000-0000-4000-8000-000000000030");

    @Test
    @DisplayName("GET /api/v1/restaurants should return 200, items envelope and X-Correlation-ID header (Spec 3.1 & 3.3)")
    void shouldReturnRestaurantsList() throws Exception {
        Restaurant restaurant = new Restaurant(restaurantId, "Burger House", true);
        when(getRestaurantsQuery.getRestaurants(20, null))
                .thenReturn(new PageResult<>(List.of(restaurant), null));

        mockMvc.perform(get("/api/v1/restaurants")
                        .header("X-Correlation-ID", correlationId.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", correlationId.toString()))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is(restaurantId.toString())))
                .andExpect(jsonPath("$.items[0].name", is("Burger House")))
                .andExpect(jsonPath("$.items[0].available", is(true)))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/restaurants/{id}/menu should return 200 and menu items with MoneyDto (Spec 3.3)")
    void shouldReturnRestaurantMenu() throws Exception {
        MenuItem item = new MenuItem(menuItemId, restaurantId, "Classic Burger", Money.cop(2500000), true, 1);
        when(getRestaurantMenuQuery.getMenu(restaurantId, 20, null))
                .thenReturn(new PageResult<>(List.of(item), null));

        mockMvc.perform(get("/api/v1/restaurants/{id}/menu", restaurantId))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name", is("Classic Burger")))
                .andExpect(jsonPath("$.items[0].price.amountMinor", is(2500000)))
                .andExpect(jsonPath("$.items[0].price.currency", is("COP")));
    }

    @Test
    @DisplayName("GET /api/v1/restaurants/{id}/menu should return 404 when restaurant not found (Spec 2.1 & 3.1)")
    void shouldReturn404WhenRestaurantNotFound() throws Exception {
        when(getRestaurantMenuQuery.getMenu(eq(restaurantId), anyInt(), any()))
                .thenThrow(new EntityNotFoundException("Restaurant not found: " + restaurantId));

        mockMvc.perform(get("/api/v1/restaurants/{id}/menu", restaurantId)
                        .header("X-Correlation-ID", correlationId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Correlation-ID", correlationId.toString()))
                .andExpect(jsonPath("$.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.correlationId", is(correlationId.toString())));
    }

    @Test
    @DisplayName("POST /api/v1/quotes should reject requests missing Idempotency-Key header (Spec 3.1)")
    void shouldRejectQuotesWithoutIdempotencyKey() throws Exception {
        String payload = "{\"restaurantId\":\"" + restaurantId + "\",\"items\":[{\"menuItemId\":\"" + menuItemId + "\",\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
                .andExpect(jsonPath("$.message", containsString("Idempotency-Key")));
    }

    @Test
    @DisplayName("POST /api/v1/quotes should reject non-UUID Idempotency-Key (Spec 3.1)")
    void shouldRejectNonUuidIdempotencyKey() throws Exception {
        String payload = "{\"restaurantId\":\"" + restaurantId + "\",\"items\":[{\"menuItemId\":\"" + menuItemId + "\",\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/quotes")
                        .header("Idempotency-Key", "not-a-valid-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
                .andExpect(jsonPath("$.message", containsString("must be a valid UUID")));
    }

    @Test
    @DisplayName("POST /api/v1/quotes should return 201 Created with Quote, total, and quoteToken (Spec 3.3)")
    void shouldCreateQuoteSuccessfully() throws Exception {
        UUID quoteId = UUID.randomUUID();
        UUID customerId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        OrderItem orderItem = new OrderItem(menuItemId, "Classic Burger", 1, Money.cop(2500000));
        Quote quote = new Quote(
                quoteId,
                customerId,
                restaurantId,
                List.of(orderItem),
                Money.cop(2500000),
                Instant.now().plusSeconds(120),
                "mock-signed-jwt"
        );

        when(createQuoteUseCase.createQuote(any(), eq(restaurantId), any(), eq(idempotencyKey.toString()), any()))
                .thenReturn(quote);

        String payload = "{\"restaurantId\":\"" + restaurantId + "\",\"items\":[{\"menuItemId\":\"" + menuItemId + "\",\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/quotes")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .header("X-Customer-ID", customerId.toString())
                        .header("X-Correlation-ID", correlationId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-ID", correlationId.toString()))
                .andExpect(jsonPath("$.id", is(quoteId.toString())))
                .andExpect(jsonPath("$.customerId", is(customerId.toString())))
                .andExpect(jsonPath("$.restaurantId", is(restaurantId.toString())))
                .andExpect(jsonPath("$.total.amountMinor", is(2500000)))
                .andExpect(jsonPath("$.total.currency", is("COP")))
                .andExpect(jsonPath("$.quoteToken", is("mock-signed-jwt")));
    }

    @Test
    @DisplayName("POST /api/v1/quotes should return 409 ITEM_UNAVAILABLE when an item is unavailable (Spec 3.3)")
    void shouldReturn409WhenItemUnavailable() throws Exception {
        when(createQuoteUseCase.createQuote(any(), eq(restaurantId), any(), any(), any()))
                .thenThrow(new ItemUnavailableException("Menu item is unavailable: Classic Burger"));

        String payload = "{\"restaurantId\":\"" + restaurantId + "\",\"items\":[{\"menuItemId\":\"" + menuItemId + "\",\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/quotes")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("ITEM_UNAVAILABLE")));
    }

    @Test
    @DisplayName("POST /api/v1/quotes should return 409 REQUEST_IN_PROGRESS with Retry-After: 1 header (Spec 3.1)")
    void shouldReturn409WithRetryAfterWhenRequestInProgress() throws Exception {
        when(createQuoteUseCase.createQuote(any(), eq(restaurantId), any(), any(), any()))
                .thenThrow(new RequestInProgressException("Concurrent request in progress"));

        String payload = "{\"restaurantId\":\"" + restaurantId + "\",\"items\":[{\"menuItemId\":\"" + menuItemId + "\",\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/quotes")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.code", is("REQUEST_IN_PROGRESS")));
    }

    @Test
    @DisplayName("POST /api/v1/quotes should return 409 IDEMPOTENCY_CONFLICT when payload does not match key (Spec 3.1)")
    void shouldReturn409OnIdempotencyConflict() throws Exception {
        when(createQuoteUseCase.createQuote(any(), eq(restaurantId), any(), any(), any()))
                .thenThrow(new IdempotencyConflictException("Payload mismatch for key"));

        String payload = "{\"restaurantId\":\"" + restaurantId + "\",\"items\":[{\"menuItemId\":\"" + menuItemId + "\",\"quantity\":1}]}";

        mockMvc.perform(post("/api/v1/quotes")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    @DisplayName("GET /api/v1/catalog/jwks.json should return 200 with public keys (Spec 2.3)")
    void shouldReturnJwksPublicKeySet() throws Exception {
        String mockJwks = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"eatsnow-catalog-key-1\"}]}";
        when(quoteSignerPort.getPublicKeyJwksJson()).thenReturn(mockJwks);

        mockMvc.perform(get("/api/v1/catalog/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys", hasSize(1)))
                .andExpect(jsonPath("$.keys[0].kid", is("eatsnow-catalog-key-1")));
    }
}
