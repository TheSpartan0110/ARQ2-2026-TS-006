package com.eatsnow.orders.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.common.domain.exception.IdempotencyConflictException;
import com.eatsnow.common.domain.exception.RequestInProgressException;
import com.eatsnow.common.domain.model.IdempotencyEntry;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.orders.domain.model.Order;
import com.eatsnow.orders.domain.model.OrderReceipt;
import com.eatsnow.orders.domain.model.Quote;
import com.eatsnow.orders.domain.port.in.CreateOrderUseCase;
import com.eatsnow.orders.domain.port.out.OrderEventPublisherPort;
import com.eatsnow.orders.domain.port.out.OrderRepositoryPort;
import com.eatsnow.orders.domain.port.out.OrderSecretPort;
import com.eatsnow.orders.domain.port.out.PaymentTokenCipherPort;
import com.eatsnow.orders.domain.port.out.QuoteVerifierPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order creation (Spec 3.4). ONE database transaction holds the idempotency record, the order, its history, the
 * encrypted token and the outbox entries. It commits before HTTP 202 is returned and never touches the broker, so a
 * broker outage cannot make order creation fail. Any failure rolls everything back, including the idempotency key
 * (validation and verification failures are not cached, Spec 3.1).
 */
@Service
public class CreateOrderService implements CreateOrderUseCase {
    private static final Logger log = LoggerFactory.getLogger(CreateOrderService.class);

    private final OrderRepositoryPort orders;
    private final OrderSecretPort secrets;
    private final QuoteVerifierPort quoteVerifier;
    private final PaymentTokenCipherPort cipher;
    private final OrderEventPublisherPort events;
    private final OrderTransitions transitions;
    private final IdempotencyStoragePort idempotency;
    private final ObjectMapper mapper;
    private final MeterRegistry registry;

    public CreateOrderService(OrderRepositoryPort orders, OrderSecretPort secrets, QuoteVerifierPort quoteVerifier,
                              PaymentTokenCipherPort cipher, OrderEventPublisherPort events,
                              OrderTransitions transitions, IdempotencyStoragePort idempotency,
                              ObjectMapper mapper, MeterRegistry registry) {
        this.orders = orders;
        this.secrets = secrets;
        this.quoteVerifier = quoteVerifier;
        this.cipher = cipher;
        this.events = events;
        this.transitions = transitions;
        this.idempotency = idempotency;
        this.mapper = mapper;
        this.registry = registry;
    }

    @Override
    @Transactional
    public OrderReceipt createOrder(Command command) {
        // Scope: authenticated subject + method + concrete path + key (Spec 3.1)
        String scopeKey = command.customerId() + ":POST:" + command.requestPath() + ":" + command.idempotencyKey();
        String requestHash = sha256(command.rawPayload());

        Optional<IdempotencyEntry> existing = idempotency.find(scopeKey);
        if (existing.isPresent()) {
            IdempotencyEntry entry = existing.get();
            if (entry.isInProgress()) {
                throw new RequestInProgressException("The same request is still being processed");
            }
            if (!entry.requestHash().equals(requestHash)) {
                throw new IdempotencyConflictException("This Idempotency-Key was already used with a different request body");
            }
            return readReceipt(entry.responseBody());     // replay of the original 202, even if the quote expired since
        }
        idempotency.start(scopeKey, requestHash);

        Quote quote = quoteVerifier.verify(command.quoteToken(), command.customerId());

        UUID orderId = UUID.randomUUID();
        Order order = Order.create(orderId, quote, command.deliveryAddress(), command.requestCorrelationId(), Instant.now());

        orders.insert(order);                                                    // 409 QUOTE_ALREADY_USED if reused
        secrets.store(orderId, cipher.encrypt(command.paymentMethodToken()));
        events.orderCreated(order);
        transitions.recordCreation(order);

        OrderReceipt receipt = new OrderReceipt(orderId, order.status().name(), order.correlationId());
        idempotency.complete(scopeKey, requestHash, 202, writeReceipt(receipt));

        registry.counter("eatsnow.orders.created").increment();
        log.info("Order accepted", kv("operation", "create order"), kv("outcome", "SUCCESS"),
                kv("orderId", orderId), kv("correlationId", order.correlationId()));
        return receipt;
    }

    private String writeReceipt(OrderReceipt receipt) {
        try {
            return mapper.writeValueAsString(receipt);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the order receipt", e);
        }
    }

    private OrderReceipt readReceipt(String json) {
        try {
            return mapper.readValue(json, OrderReceipt.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("Stored idempotent response is corrupt", e);
        }
    }

    private static String sha256(String data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.valueOf(data).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
