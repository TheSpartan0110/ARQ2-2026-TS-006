package com.eatsnow.delivery.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eatsnow.delivery.domain.model.Delivery;
import com.eatsnow.delivery.domain.model.DeliveryAddress;
import com.eatsnow.delivery.domain.model.DeliveryStatus;
import com.eatsnow.delivery.domain.model.Driver;
import com.eatsnow.delivery.domain.port.out.DeliveryEventPublisherPort;
import com.eatsnow.delivery.domain.port.out.DeliveryRepositoryPort;
import com.eatsnow.delivery.domain.port.out.DriverRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveryAssignerTest {

    private static final UUID DELIVERY_ID = UUID.fromString("00000000-0000-4000-8000-000000000070");

    @Mock DeliveryRepositoryPort deliveries;
    @Mock DriverRepositoryPort drivers;
    @Mock DeliveryEventPublisherPort events;
    @InjectMocks DeliveryAssigner assigner;

    private Delivery delivery(DeliveryStatus status) {
        return new Delivery(DELIVERY_ID, UUID.randomUUID(), UUID.randomUUID(), null,
                new DeliveryAddress("123 Example Street", "Medellin", null), status, UUID.randomUUID(),
                UUID.randomUUID(), 1, Instant.now(), Instant.now());
    }

    private Driver driver(int version) {
        return new Driver(UUID.randomUUID(), UUID.randomUUID(), true, null, null, version);
    }

    @Test
    void assignsTheDeliveryToAnAssignableDriverAndPublishesDeliveryAssigned() {
        Delivery delivery = delivery(DeliveryStatus.UNASSIGNED);
        Driver driver = driver(3);
        when(deliveries.findByIdForUpdate(DELIVERY_ID)).thenReturn(Optional.of(delivery));
        when(drivers.findAssignable(anyInt())).thenReturn(List.of(driver));
        when(drivers.claim(driver.id(), 3, DELIVERY_ID)).thenReturn(true);
        when(deliveries.updateIfVersion(any(), eq(1))).thenReturn(true);

        assertTrue(assigner.assignOne(DELIVERY_ID));

        verify(deliveries).updateIfVersion(org.mockito.ArgumentMatchers.argThat(
                (Delivery d) -> d.status() == DeliveryStatus.ASSIGNED && driver.id().equals(d.driverId())
                        && d.version() == 2), eq(1));
        verify(events).assigned(org.mockito.ArgumentMatchers.argThat((Delivery d) -> d.status() == DeliveryStatus.ASSIGNED));
    }

    @Test
    void whenAnotherDeliveryWonTheFirstDriverItTriesTheNextOne() {
        Driver taken = driver(1);
        Driver free = driver(1);
        when(deliveries.findByIdForUpdate(DELIVERY_ID)).thenReturn(Optional.of(delivery(DeliveryStatus.UNASSIGNED)));
        when(drivers.findAssignable(anyInt())).thenReturn(List.of(taken, free));
        when(drivers.claim(taken.id(), 1, DELIVERY_ID)).thenReturn(false);     // lost the race for this driver
        when(drivers.claim(free.id(), 1, DELIVERY_ID)).thenReturn(true);
        when(deliveries.updateIfVersion(any(), anyInt())).thenReturn(true);

        assertTrue(assigner.assignOne(DELIVERY_ID));

        verify(events).assigned(org.mockito.ArgumentMatchers.argThat((Delivery d) -> free.id().equals(d.driverId())));
    }

    @Test
    void withoutAnAssignableDriverTheDeliveryStaysUnassigned() {
        when(deliveries.findByIdForUpdate(DELIVERY_ID)).thenReturn(Optional.of(delivery(DeliveryStatus.UNASSIGNED)));
        when(drivers.findAssignable(anyInt())).thenReturn(List.of());

        assertFalse(assigner.assignOne(DELIVERY_ID));

        verify(deliveries, never()).updateIfVersion(any(), anyInt());
        verifyNoInteractions(events);
    }

    @Test
    void aDeliveryThatIsAlreadyAssignedIsLeftAlone() {
        when(deliveries.findByIdForUpdate(DELIVERY_ID)).thenReturn(Optional.of(delivery(DeliveryStatus.ASSIGNED)));

        assertFalse(assigner.assignOne(DELIVERY_ID));

        verifyNoInteractions(drivers, events);
    }

    @Test
    void ifTheDeliveryChangesWhileLockedTheClaimIsRolledBack() {
        Driver driver = driver(1);
        when(deliveries.findByIdForUpdate(DELIVERY_ID)).thenReturn(Optional.of(delivery(DeliveryStatus.UNASSIGNED)));
        when(drivers.findAssignable(anyInt())).thenReturn(List.of(driver));
        when(drivers.claim(driver.id(), 1, DELIVERY_ID)).thenReturn(true);
        when(deliveries.updateIfVersion(any(), anyInt())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> assigner.assignOne(DELIVERY_ID));   // the exception rolls back
        verifyNoInteractions(events);
    }
}
