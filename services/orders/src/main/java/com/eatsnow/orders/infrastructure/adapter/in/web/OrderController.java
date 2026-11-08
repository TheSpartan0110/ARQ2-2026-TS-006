package com.eatsnow.orders.infrastructure.adapter.in.web;

import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.common.web.filter.CorrelationContext;
import com.eatsnow.messaging.tracing.TraceSupport;
import com.eatsnow.orders.domain.model.OrderReceipt;
import com.eatsnow.orders.domain.port.in.CreateOrderUseCase;
import com.eatsnow.orders.domain.port.in.GetOrderQuery;
import com.eatsnow.orders.infrastructure.adapter.in.web.dto.CreateOrderRequestDto;
import com.eatsnow.orders.infrastructure.adapter.in.web.dto.OrderReceiptDto;
import com.eatsnow.orders.infrastructure.adapter.in.web.dto.OrderResponseDto;
import com.eatsnow.orders.infrastructure.adapter.in.web.dto.PaymentSummaryDto;
import com.eatsnow.security.AuthenticatedUser;
import com.eatsnow.security.BearerAuthenticationFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for Orders and the payment projection (Spec 3.4). */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderQuery getOrderQuery;
    private final ObjectMapper objectMapper;
    private final TraceSupport trace;

    public OrderController(CreateOrderUseCase createOrderUseCase, GetOrderQuery getOrderQuery,
                           ObjectMapper objectMapper, TraceSupport trace) {
        this.createOrderUseCase = createOrderUseCase;
        this.getOrderQuery = getOrderQuery;
        this.objectMapper = objectMapper;
        this.trace = trace;
    }

    /** Accepts the order once it is durably stored; never waits for Payments, Restaurants or Delivery. */
    @PostMapping
    public ResponseEntity<OrderReceiptDto> createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKeyHeader,
            @Valid @RequestBody CreateOrderRequestDto request,
            HttpServletRequest httpRequest
    ) {
        UUID idempotencyKey;
        try {
            idempotencyKey = UUID.fromString(idempotencyKeyHeader.trim());      // Spec 3.1
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Idempotency-Key must be a valid UUID");
        }

        AuthenticatedUser user = BearerAuthenticationFilter.currentUser(httpRequest);
        OrderReceipt receipt = createOrderUseCase.createOrder(new CreateOrderUseCase.Command(
                user.userId(), idempotencyKey, httpRequest.getRequestURI(), canonicalJson(request),
                request.quoteToken(), request.deliveryAddress().toDomain(), request.paymentMethodToken(),
                CorrelationContext.getCurrentCorrelationId()));
        trace.tagCurrentWorkflow(receipt.orderId(), receipt.correlationId());     // Spec 6.3

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/orders/" + receipt.orderId()))
                .body(OrderReceiptDto.fromDomain(receipt));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponseDto> getOrder(@PathVariable UUID orderId, HttpServletRequest httpRequest) {
        AuthenticatedUser user = BearerAuthenticationFilter.currentUser(httpRequest);
        return ResponseEntity.ok(OrderResponseDto.fromDomain(getOrderQuery.getOrder(user.userId(), orderId)));
    }

    @GetMapping("/{orderId}/payment")
    public ResponseEntity<PaymentSummaryDto> getPayment(@PathVariable UUID orderId, HttpServletRequest httpRequest) {
        AuthenticatedUser user = BearerAuthenticationFilter.currentUser(httpRequest);
        return ResponseEntity.ok(PaymentSummaryDto.fromDomain(getOrderQuery.getPayment(user.userId(), orderId)));
    }

    /** The request body re-serialized in a stable field order: the basis of the idempotency request hash. */
    private String canonicalJson(CreateOrderRequestDto request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Malformed request body");
        }
    }
}
