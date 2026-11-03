package com.eatsnow.delivery.application.service;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.delivery.domain.model.Delivery;
import com.eatsnow.delivery.domain.model.DeliveryStatus;
import com.eatsnow.delivery.domain.port.in.GetDeliveriesQuery;
import com.eatsnow.delivery.domain.port.out.DeliveryRepositoryPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryQueryService implements GetDeliveriesQuery {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final DeliveryRepositoryPort deliveries;

    public DeliveryQueryService(DeliveryRepositoryPort deliveries) {
        this.deliveries = deliveries;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Delivery> list(UUID driverId, DeliveryStatus status, int limit, String cursor) {
        int bounded = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return deliveries.list(driverId, status, bounded, cursor);
    }

    @Override
    @Transactional(readOnly = true)
    public Delivery get(UUID driverId, UUID deliveryId) {
        // A delivery that is not (or was never) assigned to the caller looks like it does not exist (Spec 3.6)
        return deliveries.findByIdAndDriver(deliveryId, driverId)
                .orElseThrow(() -> new EntityNotFoundException("Delivery not found"));
    }
}
