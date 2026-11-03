package com.eatsnow.delivery.application.service;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.IdempotencyConflictException;
import com.eatsnow.common.domain.exception.RequestInProgressException;
import com.eatsnow.common.domain.exception.VersionConflictException;
import com.eatsnow.common.domain.model.IdempotencyEntry;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.delivery.domain.event.AssignmentTrigger;
import com.eatsnow.delivery.domain.model.Driver;
import com.eatsnow.delivery.domain.port.in.DriverUseCase;
import com.eatsnow.delivery.domain.port.out.DriverRepositoryPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Driver record and duty switch (Spec 3.6). Going off duty never removes an active delivery: the driver finishes it and
 * simply receives no new assignments. Going on duty triggers an assignment pass after the commit.
 */
@Service
public class DriverService implements DriverUseCase {

    private final DriverRepositoryPort drivers;
    private final IdempotencyStoragePort idempotency;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper;

    public DriverService(DriverRepositoryPort drivers, IdempotencyStoragePort idempotency,
                         ApplicationEventPublisher publisher, ObjectMapper mapper) {
        this.drivers = drivers;
        this.idempotency = idempotency;
        this.publisher = publisher;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Driver ensureDriver(UUID driverId, UUID profileId) {
        drivers.insertIfAbsent(Driver.offDuty(driverId, profileId));      // no-op when it already exists
        return drivers.findById(driverId).orElseThrow(() -> new EntityNotFoundException("Driver not found"));
    }

    @Override
    @Transactional
    public Driver setDuty(DutyCommand c) {
        // Scope: authenticated subject + method + concrete path + key (Spec 3.1)
        String scopeKey = c.subjectId() + ":PUT:" + c.requestPath() + ":" + c.idempotencyKey();
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
            return read(entry.responseBody());
        }
        idempotency.start(scopeKey, requestHash);

        Driver current = drivers.findById(c.driverId()).orElseThrow(() -> new EntityNotFoundException("Driver not found"));
        if (current.onDuty() == c.onDuty()) {
            idempotency.complete(scopeKey, requestHash, 200, write(current));
            return current;                                   // setting the current value again changes nothing
        }
        if (current.version() != c.expectedVersion()) {
            throw new VersionConflictException("The driver was modified by another request", current.version());
        }
        if (!drivers.updateDuty(c.driverId(), c.expectedVersion(), c.onDuty())) {
            int latest = drivers.findById(c.driverId()).map(Driver::version).orElse(current.version());
            throw new VersionConflictException("The driver was modified by another request", latest);
        }
        Driver updated = drivers.findById(c.driverId()).orElseThrow();
        if (c.onDuty()) {
            publisher.publishEvent(new AssignmentTrigger("driver on duty"));    // handled after the commit
        }
        idempotency.complete(scopeKey, requestHash, 200, write(updated));
        return updated;
    }

    private String write(Driver driver) {
        try {
            return mapper.writeValueAsString(driver);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the driver", e);
        }
    }

    private Driver read(String json) {
        try {
            return mapper.readValue(json, Driver.class);
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
