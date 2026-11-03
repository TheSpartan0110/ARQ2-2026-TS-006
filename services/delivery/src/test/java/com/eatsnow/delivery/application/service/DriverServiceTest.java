package com.eatsnow.delivery.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eatsnow.common.domain.exception.VersionConflictException;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.delivery.domain.event.AssignmentTrigger;
import com.eatsnow.delivery.domain.model.Driver;
import com.eatsnow.delivery.domain.port.in.DriverUseCase;
import com.eatsnow.delivery.domain.port.out.DriverRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class DriverServiceTest {

    private static final UUID DRIVER = UUID.fromString("00000000-0000-4000-8000-000000000020");
    private static final UUID PROFILE = UUID.fromString("00000000-0000-4000-8000-000000000021");

    @Mock DriverRepositoryPort drivers;
    @Mock IdempotencyStoragePort idempotency;
    @Mock ApplicationEventPublisher publisher;

    private DriverService service;

    @BeforeEach
    void setUp() {
        service = new DriverService(drivers, idempotency, publisher, new ObjectMapper().findAndRegisterModules());
        when(idempotency.find(anyString())).thenReturn(Optional.empty());
    }

    private DriverUseCase.DutyCommand duty(boolean onDuty, int expectedVersion) {
        return new DriverUseCase.DutyCommand(DRIVER, PROFILE, onDuty, expectedVersion, UUID.randomUUID(),
                "/api/v1/drivers/me/duty", "{}");
    }

    @Test
    void theFirstRequestCreatesTheDriverOffDuty() {
        when(drivers.findById(DRIVER)).thenReturn(Optional.of(Driver.offDuty(DRIVER, PROFILE)));

        Driver driver = service.ensureDriver(DRIVER, PROFILE);

        verify(drivers).insertIfAbsent(Driver.offDuty(DRIVER, PROFILE));
        assertFalse(driver.onDuty());
    }

    @Test
    void goingOnDutyTriggersAnAssignmentPass() {
        Driver off = new Driver(DRIVER, PROFILE, false, null, null, 4);
        Driver on = new Driver(DRIVER, PROFILE, true, null, null, 5);
        when(drivers.findById(DRIVER)).thenReturn(Optional.of(off), Optional.of(on));
        when(drivers.updateDuty(DRIVER, 4, true)).thenReturn(true);

        Driver result = service.setDuty(duty(true, 4));

        assertTrue(result.onDuty());
        assertEquals(5, result.version());
        verify(publisher).publishEvent(org.mockito.ArgumentMatchers.any(AssignmentTrigger.class));
    }

    @Test
    void settingTheCurrentValueAgainChangesNothing() {
        Driver on = new Driver(DRIVER, PROFILE, true, null, null, 5);
        when(drivers.findById(DRIVER)).thenReturn(Optional.of(on));

        Driver result = service.setDuty(duty(true, 1));      // even with a stale version: nothing changes

        assertEquals(on, result);
        verify(drivers, never()).updateDuty(any(), anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
        verifyNoInteractions(publisher);
    }

    @Test
    void aStaleVersionIsAConflict() {
        when(drivers.findById(DRIVER)).thenReturn(Optional.of(new Driver(DRIVER, PROFILE, false, null, null, 4)));

        assertThrows(VersionConflictException.class, () -> service.setDuty(duty(true, 3)));
    }

    @Test
    void goingOffDutyKeepsAnActiveDeliveryAndDoesNotTriggerAssignment() {
        UUID active = UUID.randomUUID();
        Driver on = new Driver(DRIVER, PROFILE, true, active, null, 6);
        Driver off = new Driver(DRIVER, PROFILE, false, active, null, 7);
        when(drivers.findById(DRIVER)).thenReturn(Optional.of(on), Optional.of(off));
        when(drivers.updateDuty(DRIVER, 6, false)).thenReturn(true);

        Driver result = service.setDuty(duty(false, 6));

        assertEquals(active, result.activeDeliveryId());      // the driver finishes the active delivery
        verifyNoInteractions(publisher);
    }
}
