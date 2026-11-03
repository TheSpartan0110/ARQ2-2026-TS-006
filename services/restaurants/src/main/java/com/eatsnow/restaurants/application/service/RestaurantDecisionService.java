package com.eatsnow.restaurants.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.IdempotencyConflictException;
import com.eatsnow.common.domain.exception.RequestInProgressException;
import com.eatsnow.common.domain.exception.VersionConflictException;
import com.eatsnow.common.domain.model.IdempotencyEntry;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.restaurants.domain.model.RestaurantOrder;
import com.eatsnow.restaurants.domain.port.in.DecideRestaurantOrderUseCase;
import com.eatsnow.restaurants.domain.port.out.RestaurantEventPublisherPort;
import com.eatsnow.restaurants.domain.port.out.RestaurantOrderRepositoryPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restaurant decisions (Spec 3.5). Each successful action commits, in ONE transaction: the new status and version, the
 * idempotency record and the outbox event. The response confirms only the local decision; Orders changes later, when it
 * consumes the event. Concurrency is a conditional update on the version: of two simultaneous decisions only one wins
 * and the other receives 409 VERSION_CONFLICT.
 */
@Service
public class RestaurantDecisionService implements DecideRestaurantOrderUseCase {
    private static final Logger log = LoggerFactory.getLogger(RestaurantDecisionService.class);

    private final RestaurantOrderRepositoryPort orders;
    private final RestaurantEventPublisherPort events;
    private final IdempotencyStoragePort idempotency;
    private final ObjectMapper mapper;

    public RestaurantDecisionService(RestaurantOrderRepositoryPort orders, RestaurantEventPublisherPort events,
                                     IdempotencyStoragePort idempotency, ObjectMapper mapper) {
        this.orders = orders;
        this.events = events;
        this.idempotency = idempotency;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public RestaurantOrder accept(Command command) {
        return decide(command, "accept", order -> order.accept(Instant.now()), events::accepted);
    }

    @Override
    @Transactional
    public RestaurantOrder reject(Command command) {
        return decide(command, "reject", order -> order.reject(command.reason(), Instant.now()), events::rejected);
    }

    @Override
    @Transactional
    public RestaurantOrder startPreparation(Command command) {
        return decide(command, "preparation", order -> order.startPreparation(Instant.now()), events::preparing);
    }

    private RestaurantOrder decide(Command c, String action, Function<RestaurantOrder, RestaurantOrder> transition,
                                   Consumer<RestaurantOrder> publish) {
        // Scope: authenticated subject + method + concrete path + key (Spec 3.1)
        String scopeKey = c.subjectId() + ":POST:" + c.requestPath() + ":" + c.idempotencyKey();
        String requestHash = sha256(c.rawPayload());

        Optional<IdempotencyEntry> existing = idempotency.find(scopeKey);
        if (existing.isPresent()) {
            IdempotencyEntry entry = existing.get();
            if (entry.isInProgress()) {
                throw new RequestInProgressException("The same request is still being processed");
            }
            if (!entry.requestHash().equals(requestHash)) {
                throw new IdempotencyConflictException("This Idempotency-Key was already used with a different request body");
            }
            return read(entry.responseBody());                  // replay of the original 200
        }
        idempotency.start(scopeKey, requestHash);

        RestaurantOrder current = orders.findByIdAndRestaurant(c.orderId(), c.restaurantId())
                .orElseThrow(() -> new EntityNotFoundException("Restaurant order not found"));
        if (current.version() != c.expectedVersion()) {
            throw new VersionConflictException("The restaurant order was modified by another request",
                    current.version());
        }
        RestaurantOrder next = transition.apply(current);        // 409 INVALID_TRANSITION with details.currentStatus
        if (!orders.updateIfVersion(next, c.expectedVersion())) {
            int latest = orders.findByIdAndRestaurant(c.orderId(), c.restaurantId())
                    .map(RestaurantOrder::version).orElse(current.version());
            throw new VersionConflictException("The restaurant order was modified by another request", latest);
        }
        publish.accept(next);
        idempotency.complete(scopeKey, requestHash, 200, write(next));

        log.info("Restaurant decision committed", kv("operation", "restaurant " + action), kv("outcome", "SUCCESS"),
                kv("orderId", next.orderId()), kv("correlationId", next.correlationId()),
                kv("restaurantOrderStatus", next.status()), kv("orderVersion", next.version()));
        return next;
    }

    private String write(RestaurantOrder order) {
        try {
            return mapper.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the restaurant order", e);
        }
    }

    private RestaurantOrder read(String json) {
        try {
            return mapper.readValue(json, RestaurantOrder.class);
        } catch (JsonProcessingException e) {
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
