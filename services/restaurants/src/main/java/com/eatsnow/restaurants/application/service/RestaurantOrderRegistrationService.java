package com.eatsnow.restaurants.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.restaurants.domain.model.OrderLine;
import com.eatsnow.restaurants.domain.model.RestaurantOrder;
import com.eatsnow.restaurants.domain.port.in.RegisterRestaurantOrderUseCase;
import com.eatsnow.restaurants.domain.port.out.RestaurantOrderRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the PENDING inbox entry when RequestRestaurantAcceptance is consumed, so restaurants never see unpaid orders
 * (Spec 3.5). The orderId is the primary key: a duplicate command leaves the existing entry unchanged.
 */
@Service
public class RestaurantOrderRegistrationService implements RegisterRestaurantOrderUseCase {
    private static final Logger log = LoggerFactory.getLogger(RestaurantOrderRegistrationService.class);

    private final RestaurantOrderRepositoryPort orders;

    public RestaurantOrderRegistrationService(RestaurantOrderRepositoryPort orders) {
        this.orders = orders;
    }

    @Override
    @Transactional
    public boolean register(UUID orderId, UUID restaurantId, List<OrderLine> items, UUID correlationId) {
        boolean created = orders.insertIfAbsent(RestaurantOrder.pending(orderId, restaurantId, items, correlationId,
                Instant.now()));
        if (!created) {
            log.info("Restaurant order already exists", kv("operation", "register restaurant order"),
                    kv("outcome", "DUPLICATE"));
        }
        return created;
    }
}
