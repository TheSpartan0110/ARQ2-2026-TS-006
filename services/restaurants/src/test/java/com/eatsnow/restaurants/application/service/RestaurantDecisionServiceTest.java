package com.eatsnow.restaurants.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.IdempotencyConflictException;
import com.eatsnow.common.domain.exception.InvalidTransitionException;
import com.eatsnow.common.domain.exception.VersionConflictException;
import com.eatsnow.common.domain.model.IdempotencyEntry;
import com.eatsnow.common.domain.model.Money;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.restaurants.domain.model.OrderLine;
import com.eatsnow.restaurants.domain.model.RestaurantOrder;
import com.eatsnow.restaurants.domain.model.RestaurantOrderStatus;
import com.eatsnow.restaurants.domain.port.in.DecideRestaurantOrderUseCase;
import com.eatsnow.restaurants.domain.port.out.RestaurantEventPublisherPort;
import com.eatsnow.restaurants.domain.port.out.RestaurantOrderRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestaurantDecisionServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private static final UUID RESTAURANT = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID USER = UUID.fromString("00000000-0000-4000-8000-000000000050");
    private static final UUID KEY = UUID.fromString("00000000-0000-4000-8000-000000000060");
    private static final String RAW = "{\"expectedVersion\":1}";

    @Mock RestaurantOrderRepositoryPort orders;
    @Mock RestaurantEventPublisherPort events;
    @Mock IdempotencyStoragePort idempotency;

    private RestaurantDecisionService service;

    @BeforeEach
    void setUp() {
        service = new RestaurantDecisionService(orders, events, idempotency, new ObjectMapper().findAndRegisterModules());
    }

    private RestaurantOrder order(RestaurantOrderStatus status, int version) {
        return new RestaurantOrder(ORDER_ID, RESTAURANT,
                List.of(new OrderLine(UUID.randomUUID(), "Classic Burger", 1, Money.cop(2500000))), status, null,
                UUID.randomUUID(), version, Instant.now(), Instant.now());
    }

    private DecideRestaurantOrderUseCase.Command command(int expectedVersion, String reason) {
        return new DecideRestaurantOrderUseCase.Command(RESTAURANT, USER, ORDER_ID, expectedVersion, reason, KEY,
                "/api/v1/restaurant-orders/" + ORDER_ID + "/accept", RAW);
    }

    private static String scopeKey() {
        return USER + ":POST:/api/v1/restaurant-orders/" + ORDER_ID + "/accept:" + KEY;
    }

    private static String hash(String data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
    }

    private void given(RestaurantOrder order) {
        when(idempotency.find(anyString())).thenReturn(Optional.empty());
        when(orders.findByIdAndRestaurant(ORDER_ID, RESTAURANT)).thenReturn(Optional.of(order));
        when(orders.updateIfVersion(any(), anyInt())).thenReturn(true);
    }

    @Test
    void acceptCommitsTheDecisionTheIdempotencyRecordAndTheEventTogether() {
        given(order(RestaurantOrderStatus.PENDING, 1));

        RestaurantOrder result = service.accept(command(1, null));

        assertEquals(RestaurantOrderStatus.ACCEPTED, result.status());
        assertEquals(2, result.version());
        verify(orders).updateIfVersion(result, 1);
        verify(events).accepted(result);
        verify(idempotency).complete(eq(scopeKey()), anyString(), eq(200), anyString());
    }

    @Test
    void rejectStoresTheReasonAndPublishesRestaurantRejected() {
        given(order(RestaurantOrderStatus.PENDING, 1));

        RestaurantOrder result = service.reject(command(1, "Kitchen closed due to a gas outage"));

        assertEquals(RestaurantOrderStatus.REJECTED, result.status());
        assertEquals("Kitchen closed due to a gas outage", result.reason());
        verify(events).rejected(result);
    }

    @Test
    void preparationOnlyStartsAfterAcceptance() {
        given(order(RestaurantOrderStatus.PENDING, 1));

        InvalidTransitionException error = assertThrows(InvalidTransitionException.class,
                () -> service.startPreparation(command(1, null)));

        assertEquals("PENDING", ((java.util.Map<?, ?>) error.getDetails()).get("currentStatus"));
        verifyNoInteractions(events);
    }

    @Test
    void acceptingAnAlreadyRejectedOrderIsAnInvalidTransition() {
        given(order(RestaurantOrderStatus.REJECTED, 2));

        assertThrows(InvalidTransitionException.class, () -> service.accept(command(2, null)));
        verify(orders, never()).updateIfVersion(any(), anyInt());
    }

    @Test
    void aStaleExpectedVersionIsAVersionConflictThatReportsTheCurrentVersion() {
        given(order(RestaurantOrderStatus.ACCEPTED, 2));

        VersionConflictException error = assertThrows(VersionConflictException.class,
                () -> service.accept(command(1, null)));

        assertEquals(2, ((java.util.Map<?, ?>) error.getDetails()).get("currentVersion"));
        verifyNoInteractions(events);
    }

    @Test
    void ofTwoConcurrentDecisionsOnlyTheConditionalUpdateWinnerPublishes() {
        given(order(RestaurantOrderStatus.PENDING, 1));
        when(orders.updateIfVersion(any(), anyInt())).thenReturn(false);     // another decision won the race

        assertThrows(VersionConflictException.class, () -> service.accept(command(1, null)));
        verifyNoInteractions(events);
    }

    @Test
    void anotherRestaurantsOrderLooksLikeItDoesNotExist() {
        when(idempotency.find(anyString())).thenReturn(Optional.empty());
        when(orders.findByIdAndRestaurant(ORDER_ID, RESTAURANT)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.accept(command(1, null)));
        verifyNoInteractions(events);
    }

    @Test
    void theSameIdempotencyKeyReplaysTheOriginalResponse() throws Exception {
        String stored = new ObjectMapper().findAndRegisterModules().writeValueAsString(order(RestaurantOrderStatus.ACCEPTED, 2));
        when(idempotency.find(scopeKey())).thenReturn(Optional.of(
                new IdempotencyEntry(scopeKey(), hash(RAW), "COMPLETED", 200, stored, Instant.now())));

        RestaurantOrder replay = service.accept(command(1, null));

        assertEquals(RestaurantOrderStatus.ACCEPTED, replay.status());
        verifyNoInteractions(orders, events);
    }

    @Test
    void theSameKeyWithAnotherBodyIsAConflict() {
        when(idempotency.find(scopeKey())).thenReturn(Optional.of(
                new IdempotencyEntry(scopeKey(), "other", "COMPLETED", 200, "{}", Instant.now())));

        assertThrows(IdempotencyConflictException.class, () -> service.accept(command(1, null)));
    }
}
