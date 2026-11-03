package com.eatsnow.delivery.domain.port.out;

import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.delivery.domain.model.Delivery;
import com.eatsnow.delivery.domain.model.DeliveryStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepositoryPort {
    /** @return false if a delivery already exists for the orderId */
    boolean insertIfAbsent(Delivery delivery);

    /** Row lock: serializes assignment and actions on one delivery. */
    Optional<Delivery> findByIdForUpdate(UUID deliveryId);

    /** Only returns the delivery when it is assigned (now or before) to that driver. */
    Optional<Delivery> findByIdAndDriver(UUID deliveryId, UUID driverId);

    PageResult<Delivery> list(UUID driverId, DeliveryStatus status, int limit, String cursor);

    /** Ids of UNASSIGNED deliveries, first-come first-served by createdAt. */
    List<UUID> findUnassignedIds(int limit);

    /** @return false when the stored version no longer equals expectedVersion */
    boolean updateIfVersion(Delivery updated, int expectedVersion);

    long oldestUnassignedSeconds();
}
