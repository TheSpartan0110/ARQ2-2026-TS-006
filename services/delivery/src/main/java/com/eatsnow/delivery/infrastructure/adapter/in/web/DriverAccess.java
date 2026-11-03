package com.eatsnow.delivery.infrastructure.adapter.in.web;

import com.eatsnow.common.domain.exception.ForbiddenException;
import com.eatsnow.delivery.domain.port.in.DriverUseCase;
import com.eatsnow.security.AuthenticatedUser;
import com.eatsnow.security.BearerAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves the calling driver. The driverId comes from the verified token, never from the path, query or body, and the
 * Driver record is created with onDuty=false on the first authenticated request carrying a new driverId (Spec 3.6).
 */
@Component
public class DriverAccess {

    private final DriverUseCase drivers;

    public DriverAccess(DriverUseCase drivers) {
        this.drivers = drivers;
    }

    public AuthenticatedUser user(HttpServletRequest request) {
        return BearerAuthenticationFilter.currentUser(request);
    }

    public UUID driverId(HttpServletRequest request) {
        AuthenticatedUser user = user(request);
        if (user.driverId() == null) {
            throw new ForbiddenException("The driver profile is not linked to a driver");
        }
        drivers.ensureDriver(user.driverId(), user.userId());
        return user.driverId();
    }
}
