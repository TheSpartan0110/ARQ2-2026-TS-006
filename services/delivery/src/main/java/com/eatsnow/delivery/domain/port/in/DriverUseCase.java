package com.eatsnow.delivery.domain.port.in;

import com.eatsnow.delivery.domain.model.Driver;
import java.util.UUID;

/** GET /drivers/me and PUT /drivers/me/duty (Spec 3.6). */
public interface DriverUseCase {

    record DutyCommand(UUID driverId, UUID subjectId, boolean onDuty, int expectedVersion, UUID idempotencyKey,
                       String requestPath, String rawPayload) { }

    /** Creates the Driver with onDuty=false on the first authenticated request carrying a new driverId. */
    Driver ensureDriver(UUID driverId, UUID profileId);

    Driver setDuty(DutyCommand command);
}
