package com.eatsnow.delivery.application.service;

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
import com.eatsnow.common.domain.exception.InvalidTransitionException;
import com.eatsnow.common.domain.exception.VersionConflictException;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.delivery.domain.event.AssignmentTrigger;
import com.eatsnow.delivery.domain.model.Delivery;
import com.eatsnow.delivery.domain.model.DeliveryAddress;
import com.eatsnow.delivery.domain.model.DeliveryStatus;
import com.eatsnow.delivery.domain.port.in.DeliveryActionUseCase;
import com.eatsnow.delivery.domain.port.out.DeliveryEventPublisherPort;
import com.eatsnow.delivery.domain.port.out.DeliveryRepositoryPort;
import com.eatsnow.delivery.domain.port.out.DriverRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveryActionServiceTest {

    private static final UUID DELIVERY_ID = UUID.fromString("00000000-0000-4000-8000-000000000070");
    private static final UUID DRIVER = UUID.fromString("00000000-0000-4000-8000-000000000020");
    private static final UUID USER = UUID.fromString("00000000-0000-4000-8000-000000000021");
    private static final UUID KEY = UUID.fromString("00000000-0000-4000-8000-000000000071");

    @Mock DeliveryRepositoryPort deliveries;
    @Mock DriverRepositoryPort drivers;
    @Mock DeliveryEventPublisherPort events;
    @Mock IdempotencyStoragePort idempotency;
    @Mock ApplicationEventPublisher publisher;

    private DeliveryActionService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryActionService(deliveries, drivers, events, idempotency, publisher,
                new ObjectMapper().findAndRegisterModules());
    }

    private Delivery delivery(DeliveryStatus status, int version) {
        return new Delivery(DELIVERY_ID, UUID.randomUUID(), UUID.randomUUID(), DRIVER,
                new DeliveryAddress("123 Example Street", "Medellin", null), status, UUID.randomUUID(),
                UUID.randomUUID(), version, Instant.now(), Instant.now());
    }

    private DeliveryActionUseCase.Command command(int expectedVersion) {
        return new DeliveryActionUseCase.Command(DRIVER, USER, DELIVERY_ID, expectedVersion, KEY,
                "/api/v1/deliveries/" + DELIVERY_ID + "/pickup", "{\"expectedVersion\":" + expectedVersion + "}");
    }

    private void given(Delivery delivery) {
        when(idempotency.find(anyString())).thenReturn(Optional.empty());
        when(deliveries.findByIdAndDriver(DELIVERY_ID, DRIVER)).thenReturn(Optional.of(delivery));
        when(deliveries.updateIfVersion(any(), anyInt())).thenReturn(true);
    }

    @Test
    void pickupMovesAssignedToPickedUpAndPublishesDeliveryPickedUp() {
        given(delivery(DeliveryStatus.ASSIGNED, 2));

        Delivery result = service.pickUp(command(2));

        assertEquals(DeliveryStatus.PICKED_UP, result.status());
        assertEquals(3, result.version());
        verify(events).pickedUp(result);
        verify(idempotency).complete(anyString(), anyString(), eq(200), anyString());
    }

    @Test
    void completionFreesTheDriverPublishesDeliveryCompletedAndTriggersANewAssignment() {
        given(delivery(DeliveryStatus.PICKED_UP, 3));

        Delivery result = service.complete(command(3));

        assertEquals(DeliveryStatus.DELIVERED, result.status());
        verify(drivers).release(DRIVER, DELIVERY_ID);
        verify(events).completed(result);
        verify(publisher).publishEvent(org.mockito.ArgumentMatchers.any(AssignmentTrigger.class));
    }

    @Test
    void assignmentAloneNeverMeansPickedUp() {
        given(delivery(DeliveryStatus.UNASSIGNED, 1));

        assertThrows(InvalidTransitionException.class, () -> service.pickUp(command(1)));
        verifyNoInteractions(events);
    }

    @Test
    void completionRequiresPickupFirst() {
        given(delivery(DeliveryStatus.ASSIGNED, 2));

        assertThrows(InvalidTransitionException.class, () -> service.complete(command(2)));
        verify(drivers, never()).release(any(), any());
    }

    @Test
    void aStaleVersionIsAConflictThatReportsTheCurrentVersion() {
        given(delivery(DeliveryStatus.PICKED_UP, 3));

        VersionConflictException error = assertThrows(VersionConflictException.class, () -> service.complete(command(2)));

        assertEquals(3, ((java.util.Map<?, ?>) error.getDetails()).get("currentVersion"));
        verifyNoInteractions(events);
    }

    @Test
    void aDeliveryOfAnotherDriverLooksLikeItDoesNotExist() {
        when(idempotency.find(anyString())).thenReturn(Optional.empty());
        when(deliveries.findByIdAndDriver(DELIVERY_ID, DRIVER)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.pickUp(command(1)));
    }
}
