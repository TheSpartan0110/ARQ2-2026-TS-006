package com.eatsnow.catalog.infrastructure.adapter.in.web;

import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.catalog.domain.model.MenuItem;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.catalog.domain.model.Quote;
import com.eatsnow.catalog.domain.model.RequestedItem;
import com.eatsnow.catalog.domain.model.Restaurant;
import com.eatsnow.catalog.domain.port.in.CreateQuoteUseCase;
import com.eatsnow.catalog.domain.port.in.GetRestaurantMenuQuery;
import com.eatsnow.catalog.domain.port.in.GetRestaurantsQuery;
import com.eatsnow.catalog.domain.port.out.QuoteSignerPort;
import com.eatsnow.catalog.infrastructure.adapter.in.web.dto.CreateQuoteRequestDto;
import com.eatsnow.catalog.infrastructure.adapter.in.web.dto.MenuItemResponseDto;
import com.eatsnow.common.web.dto.PaginatedResponseDto;
import com.eatsnow.catalog.infrastructure.adapter.in.web.dto.QuoteResponseDto;
import com.eatsnow.catalog.infrastructure.adapter.in.web.dto.RequestedItemRequestDto;
import com.eatsnow.catalog.infrastructure.adapter.in.web.dto.RestaurantResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Catalog and Quotes (Spec 3.3).
 */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final GetRestaurantsQuery getRestaurantsQuery;
    private final GetRestaurantMenuQuery getRestaurantMenuQuery;
    private final CreateQuoteUseCase createQuoteUseCase;
    private final QuoteSignerPort quoteSignerPort;
    private final ObjectMapper objectMapper;

    public CatalogController(
            GetRestaurantsQuery getRestaurantsQuery,
            GetRestaurantMenuQuery getRestaurantMenuQuery,
            CreateQuoteUseCase createQuoteUseCase,
            QuoteSignerPort quoteSignerPort,
            ObjectMapper objectMapper
    ) {
        this.getRestaurantsQuery = getRestaurantsQuery;
        this.getRestaurantMenuQuery = getRestaurantMenuQuery;
        this.createQuoteUseCase = createQuoteUseCase;
        this.quoteSignerPort = quoteSignerPort;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/restaurants")
    public ResponseEntity<PaginatedResponseDto<RestaurantResponseDto>> getRestaurants(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor
    ) {
        PageResult<Restaurant> result = getRestaurantsQuery.getRestaurants(limit, cursor);
        return ResponseEntity.ok(PaginatedResponseDto.fromDomain(result, RestaurantResponseDto::fromDomain));
    }

    @GetMapping("/restaurants/{restaurantId}/menu")
    public ResponseEntity<PaginatedResponseDto<MenuItemResponseDto>> getMenu(
            @PathVariable UUID restaurantId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor
    ) {
        PageResult<MenuItem> result = getRestaurantMenuQuery.getMenu(restaurantId, limit, cursor);
        return ResponseEntity.ok(PaginatedResponseDto.fromDomain(result, MenuItemResponseDto::fromDomain));
    }

    @PostMapping("/quotes")
    public ResponseEntity<QuoteResponseDto> createQuote(
            @RequestHeader("Idempotency-Key") String idempotencyKeyHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Customer-ID", required = false) String customerIdHeader,
            @Valid @RequestBody CreateQuoteRequestDto request
    ) {
        // Validate Idempotency-Key UUID format (Spec 3.1)
        try {
            UUID.fromString(idempotencyKeyHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Idempotency-Key must be a valid UUID");
        }

        UUID customerId = resolveCustomerId(authHeader, customerIdHeader);

        List<RequestedItem> items = request.items().stream()
                .map(RequestedItemRequestDto::toDomain)
                .toList();

        String rawPayload;
        try {
            rawPayload = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            rawPayload = request.toString();
        }

        Quote quote = createQuoteUseCase.createQuote(
                customerId,
                request.restaurantId(),
                items,
                idempotencyKeyHeader.trim(),
                rawPayload
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(QuoteResponseDto.fromDomain(quote));
    }

    @GetMapping(value = "/catalog/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getJwks() {
        return ResponseEntity.ok(quoteSignerPort.getPublicKeyJwksJson());
    }

    private UUID resolveCustomerId(String authHeader, String customerIdHeader) {
        if (customerIdHeader != null && !customerIdHeader.isBlank()) {
            try {
                return UUID.fromString(customerIdHeader.trim());
            } catch (IllegalArgumentException ignored) {}
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                SignedJWT jwt = SignedJWT.parse(token);
                String subject = jwt.getJWTClaimsSet().getSubject();
                if (subject != null) {
                    return UUID.fromString(subject);
                }
            } catch (Exception ignored) {}
        }

        // Default test customer from spec example if no token provided during early wave testing
        return UUID.fromString("00000000-0000-4000-8000-000000000001");
    }
}
