package com.eatsnow.delivery.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.IdempotencyConflictException;
import com.eatsnow.common.domain.exception.RequestInProgressException;
import com.eatsnow.common.domain.exception.VersionConflictException;
import com.eatsnow.common.domain.model.IdempotencyEntry;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.delivery.domain.event.AssignmentTrigger;
import com.eatsnow.delivery.domain.model.Delivery;
import com.eatsnow.delivery.domain.port.in.DeliveryActionUseCase;
import com.eatsnow.delivery.domain.port.out.DeliveryEventPublisherPort;
import com.eatsnow.delivery.domain.port.out.DeliveryRepositoryPort;
import com.eatsnow.delivery.domain.port.out.DriverRepositoryPort;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Driver actions on a delivery (Spec 3.6). Each one commits the Delivery (and the Driver on completion), the idempotency
 * record and the outbox event in ONE transaction; the response confirms only the local action. Completion frees the
 * driver and triggers a new assignment pass after the commit.
 */
@Service
public class DeliveryActionService implements DeliveryActionUseCase {
    private static final Logger log = LoggerFactory.getLogger(DeliveryActionService.class);

    private final DeliveryRepositoryPort deliveries;
    private final DriverRepositoryPort drivers;
    private final DeliveryEventPublisherPort events;
    private final IdempotencyStoragePort idempotency;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper;

    public DeliveryActionService(DeliveryRepositoryPort deliveries, DriverRepositoryPort drivers,
                                 DeliveryEventPublisherPort events, IdempotencyStoragePort idempotency,
                                 ApplicationEventPublisher publisher, ObjectMapper mapper) {
        this.deliveries = deliveries;
        this.drivers = drivers;
        this.events = events;
        this.idempotency = idempotency;
        this.publisher = publisher;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Delivery pickUp(Command command) {
        return act(command, "pickup", delivery -> delivery.pickUp(Instant.now()), events::pickedUp);
    }

    @Override
    @Transactional
    public Delivery complete(Command command) {
        return act(command, "completion", delivery -> delivery.complete(Instant.now()), delivered -> {
            drivers.release(command.driverId(), delivered.id());            // activeDeliveryId cleared (Spec 3.6)
            events.completed(delivered);
            publisher.publishEvent(new AssignmentTrigger("driver available"));   // handled after the commit
        });
    }

    private Delivery act(Command c, String action, Function<Delivery, Delivery> transition, Consumer<Delivery> publish) {
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

        Delivery current = deliveries.findByIdAndDriver(c.deliveryId(), c.driverId())
                .orElseThrow(() -> new EntityNotFoundException("Delivery not found"));
        if (current.version() != c.expectedVersion()) {
            throw new VersionConflictException("The delivery was modified by another request", current.version());
        }
        Delivery next = transition.apply(current);               // 409 INVALID_TRANSITION with details.currentStatus
        if (!deliveries.updateIfVersion(next, c.expectedVersion())) {
            int latest = deliveries.findByIdAndDriver(c.deliveryId(), c.driverId())
                    .map(Delivery::version).orElse(current.version());
            throw new VersionConflictException("The delivery was modified by another request", latest);
        }
        publish.accept(next);
        idempotency.complete(scopeKey, requestHash, 200, write(next));

        log.info("Driver action committed", kv("operation", "delivery " + action), kv("outcome", "SUCCESS"),
                kv("orderId", next.orderId()), kv("correlationId", next.correlationId()),
                kv("deliveryStatus", next.status()), kv("deliveryVersion", next.version()));
        return next;
    }

    private String write(Delivery delivery) {
        try {
            return mapper.writeValueAsString(delivery);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the delivery", e);
        }
    }

    private Delivery read(String json) {
        try {
            return mapper.readValue(json, Delivery.class);
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
