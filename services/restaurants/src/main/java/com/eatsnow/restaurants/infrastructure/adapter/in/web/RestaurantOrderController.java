package com.eatsnow.restaurants.infrastructure.adapter.in.web;

import com.eatsnow.common.domain.exception.ForbiddenException;
import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.common.web.dto.PaginatedResponseDto;
import com.eatsnow.messaging.tracing.TraceSupport;
import com.eatsnow.restaurants.domain.model.RestaurantOrder;
import com.eatsnow.restaurants.domain.model.RestaurantOrderStatus;
import com.eatsnow.restaurants.domain.port.in.DecideRestaurantOrderUseCase;
import com.eatsnow.restaurants.domain.port.in.GetRestaurantOrdersQuery;
import com.eatsnow.restaurants.infrastructure.adapter.in.web.dto.DecisionRequestDto;
import com.eatsnow.restaurants.infrastructure.adapter.in.web.dto.RejectRequestDto;
import com.eatsnow.restaurants.infrastructure.adapter.in.web.dto.RestaurantOrderResponseDto;
import com.eatsnow.security.AuthenticatedUser;
import com.eatsnow.security.BearerAuthenticationFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Restaurant inbox and decisions (Spec 3.5). The caller's restaurantId comes from the verified token, never from the
 * path, query or body. Only the RESTAURANT role reaches this controller (security filter).
 */
@RestController
@RequestMapping("/api/v1/restaurant-orders")
public class RestaurantOrderController {

    private final GetRestaurantOrdersQuery queries;
    private final DecideRestaurantOrderUseCase decisions;
    private final ObjectMapper objectMapper;
    private final TraceSupport trace;

    public RestaurantOrderController(GetRestaurantOrdersQuery queries, DecideRestaurantOrderUseCase decisions,
                                     ObjectMapper objectMapper, TraceSupport trace) {
        this.queries = queries;
        this.decisions = decisions;
        this.objectMapper = objectMapper;
        this.trace = trace;
    }

    @GetMapping
    public ResponseEntity<PaginatedResponseDto<RestaurantOrderResponseDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        PageResult<RestaurantOrder> page = queries.list(restaurantId(request), parseStatus(status), limit, cursor);
        return ResponseEntity.ok(PaginatedResponseDto.fromDomain(page, RestaurantOrderResponseDto::fromDomain));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<RestaurantOrderResponseDto> get(@PathVariable UUID orderId, HttpServletRequest request) {
        return ResponseEntity.ok(RestaurantOrderResponseDto.fromDomain(queries.get(restaurantId(request), orderId)));
    }

    @PostMapping("/{orderId}/accept")
    public ResponseEntity<RestaurantOrderResponseDto> accept(
            @PathVariable UUID orderId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody DecisionRequestDto body, HttpServletRequest request) {
        return decide(orderId, key, body.expectedVersion(), null, body, request, decisions::accept);
    }

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<RestaurantOrderResponseDto> reject(
            @PathVariable UUID orderId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RejectRequestDto body, HttpServletRequest request) {
        return decide(orderId, key, body.expectedVersion(), body.reason().trim(), body, request, decisions::reject);
    }

    @PostMapping("/{orderId}/preparation")
    public ResponseEntity<RestaurantOrderResponseDto> preparation(
            @PathVariable UUID orderId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody DecisionRequestDto body, HttpServletRequest request) {
        return decide(orderId, key, body.expectedVersion(), null, body, request, decisions::startPreparation);
    }

    private ResponseEntity<RestaurantOrderResponseDto> decide(
            UUID orderId, String idempotencyKeyHeader, int expectedVersion, String reason, Object body,
            HttpServletRequest request, Function<DecideRestaurantOrderUseCase.Command, RestaurantOrder> action) {
        UUID idempotencyKey;
        try {
            idempotencyKey = UUID.fromString(idempotencyKeyHeader.trim());       // Spec 3.1
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Idempotency-Key must be a valid UUID");
        }
        AuthenticatedUser user = BearerAuthenticationFilter.currentUser(request);
        RestaurantOrder result = action.apply(new DecideRestaurantOrderUseCase.Command(restaurantId(request),
                user.userId(), orderId, expectedVersion, reason, idempotencyKey, request.getRequestURI(),
                canonicalJson(body)));
        // Spec 6.3: this action starts its own trace; tag it with the stored workflow correlation id.
        trace.tagCurrentWorkflow(result.orderId(), result.correlationId());
        return ResponseEntity.ok(RestaurantOrderResponseDto.fromDomain(result));
    }

    private UUID restaurantId(HttpServletRequest request) {
        UUID restaurantId = BearerAuthenticationFilter.currentUser(request).restaurantId();
        if (restaurantId == null) {
            throw new ForbiddenException("The restaurant profile is not linked to a restaurant");
        }
        return restaurantId;
    }

    private static RestaurantOrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RestaurantOrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("status must be one of PENDING, ACCEPTED, REJECTED, PREPARING");
        }
    }

    private String canonicalJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Malformed request body");
        }
    }
}
