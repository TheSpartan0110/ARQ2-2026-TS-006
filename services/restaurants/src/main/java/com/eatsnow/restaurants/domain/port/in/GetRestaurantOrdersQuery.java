package com.eatsnow.restaurants.domain.port.in;

import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.restaurants.domain.model.RestaurantOrder;
import com.eatsnow.restaurants.domain.model.RestaurantOrderStatus;
import java.util.UUID;

/** GET /restaurant-orders. The restaurant comes from the verified token, never from the request (Spec 3.5). */
public interface GetRestaurantOrdersQuery {
    PageResult<RestaurantOrder> list(UUID restaurantId, RestaurantOrderStatus status, int limit, String cursor);

    /** @throws com.eatsnow.common.domain.exception.EntityNotFoundException absent or foreign orders (404) */
    RestaurantOrder get(UUID restaurantId, UUID orderId);
}
