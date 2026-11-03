package com.eatsnow.delivery.infrastructure.adapter.in.web;

import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.delivery.domain.port.in.DriverUseCase;
import com.eatsnow.delivery.infrastructure.adapter.in.web.dto.DriverResponseDto;
import com.eatsnow.delivery.infrastructure.adapter.in.web.dto.DutyRequestDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Own driver record and duty switch (Spec 3.6). Only the DRIVER role reaches this controller. */
@RestController
@RequestMapping("/api/v1/drivers/me")
public class DriverController {

    private final DriverUseCase drivers;
    private final DriverAccess access;
    private final ObjectMapper objectMapper;

    public DriverController(DriverUseCase drivers, DriverAccess access, ObjectMapper objectMapper) {
        this.drivers = drivers;
        this.access = access;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<DriverResponseDto> me(HttpServletRequest request) {
        UUID driverId = access.driverId(request);
        return ResponseEntity.ok(DriverResponseDto.fromDomain(drivers.ensureDriver(driverId, access.user(request).userId())));
    }

    @PutMapping("/duty")
    public ResponseEntity<DriverResponseDto> setDuty(@RequestHeader("Idempotency-Key") String idempotencyKeyHeader,
                                                     @Valid @RequestBody DutyRequestDto body,
                                                     HttpServletRequest request) {
        UUID idempotencyKey;
        try {
            idempotencyKey = UUID.fromString(idempotencyKeyHeader.trim());     // Spec 3.1
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Idempotency-Key must be a valid UUID");
        }
        UUID driverId = access.driverId(request);
        String raw;
        try {
            raw = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Malformed request body");
        }
        return ResponseEntity.ok(DriverResponseDto.fromDomain(drivers.setDuty(new DriverUseCase.DutyCommand(
                driverId, access.user(request).userId(), body.onDuty(), body.expectedVersion(), idempotencyKey,
                request.getRequestURI(), raw))));
    }
}
