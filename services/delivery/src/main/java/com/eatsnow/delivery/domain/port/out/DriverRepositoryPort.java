package com.eatsnow.delivery.domain.port.out;

import com.eatsnow.delivery.domain.model.Driver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepositoryPort {
    /** @return false if the driver already exists */
    boolean insertIfAbsent(Driver driver);

    Optional<Driver> findById(UUID driverId);

    /** On duty, no active delivery, ordered by the longest time since the last assignment (never assigned first). */
    List<Driver> findAssignable(int limit);

    /**
     * Conditional claim: WHERE id = ? AND onDuty = true AND activeDeliveryId IS NULL AND version = ?. Only one
     * delivery can win a driver, so a driver never has two active deliveries (Spec 3.6).
     */
    boolean claim(UUID driverId, int expectedVersion, UUID deliveryId);

    /** Clears activeDeliveryId when its delivery is completed. */
    void release(UUID driverId, UUID deliveryId);

    /** @return false when the stored version no longer equals expectedVersion */
    boolean updateDuty(UUID driverId, int expectedVersion, boolean onDuty);
}
