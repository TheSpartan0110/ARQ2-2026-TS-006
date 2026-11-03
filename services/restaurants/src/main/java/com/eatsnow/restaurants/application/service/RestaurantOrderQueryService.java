package com.eatsnow.restaurants.application.service;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.restaurants.domain.model.RestaurantOrder;
import com.eatsnow.restaurants.domain.model.RestaurantOrderStatus;
import com.eatsnow.restaurants.domain.port.in.GetRestaurantOrdersQuery;
import com.eatsnow.restaurants.domain.port.out.RestaurantOrderRepositoryPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantOrderQueryService implements GetRestaurantOrdersQuery {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final RestaurantOrderRepositoryPort orders;

    public RestaurantOrderQueryService(RestaurantOrderRepositoryPort orders) {
        this.orders = orders;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RestaurantOrder> list(UUID restaurantId, RestaurantOrderStatus status, int limit, String cursor) {
        int bounded = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return orders.list(restaurantId, status, bounded, cursor);
    }

    @Override
    @Transactional(readOnly = true)
    public RestaurantOrder get(UUID restaurantId, UUID orderId) {
        // Foreign and absent orders are indistinguishable: 404 prevents enumeration (Spec 3.5)
        return orders.findByIdAndRestaurant(orderId, restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant order not found"));
    }
}
