package com.eatsnow.delivery.infrastructure.adapter.in.web;

import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.common.web.dto.PaginatedResponseDto;
import com.eatsnow.delivery.domain.model.Delivery;
import com.eatsnow.delivery.domain.model.DeliveryStatus;
import com.eatsnow.delivery.domain.port.in.DeliveryActionUseCase;
import com.eatsnow.delivery.domain.port.in.GetDeliveriesQuery;
import com.eatsnow.delivery.infrastructure.adapter.in.web.dto.ActionRequestDto;
import com.eatsnow.delivery.infrastructure.adapter.in.web.dto.DeliveryResponseDto;
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

/** Deliveries of the calling driver (Spec 3.6). The driverId comes from the verified token. */
@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {

    private final GetDeliveriesQuery queries;
    private final DeliveryActionUseCase actions;
    private final DriverAccess access;
    private final ObjectMapper objectMapper;

    public DeliveryController(GetDeliveriesQuery queries, DeliveryActionUseCase actions, DriverAccess access,
                              ObjectMapper objectMapper) {
        this.queries = queries;
        this.actions = actions;
        this.access = access;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<PaginatedResponseDto<DeliveryResponseDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        PageResult<Delivery> page = queries.list(access.driverId(request), parseStatus(status), limit, cursor);
        return ResponseEntity.ok(PaginatedResponseDto.fromDomain(page, DeliveryResponseDto::fromDomain));
    }

    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResponseDto> get(@PathVariable UUID deliveryId, HttpServletRequest request) {
        return ResponseEntity.ok(DeliveryResponseDto.fromDomain(queries.get(access.driverId(request), deliveryId)));
    }

    @PostMapping("/{deliveryId}/pickup")
    public ResponseEntity<DeliveryResponseDto> pickup(@PathVariable UUID deliveryId,
                                                      @RequestHeader("Idempotency-Key") String key,
                                                      @Valid @RequestBody ActionRequestDto body,
                                                      HttpServletRequest request) {
        return act(deliveryId, key, body, request, actions::pickUp);
    }

    @PostMapping("/{deliveryId}/completion")
    public ResponseEntity<DeliveryResponseDto> completion(@PathVariable UUID deliveryId,
                                                          @RequestHeader("Idempotency-Key") String key,
                                                          @Valid @RequestBody ActionRequestDto body,
                                                          HttpServletRequest request) {
        return act(deliveryId, key, body, request, actions::complete);
    }

    private ResponseEntity<DeliveryResponseDto> act(UUID deliveryId, String idempotencyKeyHeader,
                                                    ActionRequestDto body, HttpServletRequest request,
                                                    Function<DeliveryActionUseCase.Command, Delivery> action) {
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
        Delivery result = action.apply(new DeliveryActionUseCase.Command(driverId, access.user(request).userId(),
                deliveryId, body.expectedVersion(), idempotencyKey, request.getRequestURI(), raw));
        return ResponseEntity.ok(DeliveryResponseDto.fromDomain(result));
    }

    private static DeliveryStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DeliveryStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("status must be one of UNASSIGNED, ASSIGNED, PICKED_UP, DELIVERED");
        }
    }
}
