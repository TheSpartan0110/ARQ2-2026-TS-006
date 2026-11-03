package com.eatsnow.restaurants.domain.port.out;

import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.restaurants.domain.model.RestaurantOrder;
import com.eatsnow.restaurants.domain.model.RestaurantOrderStatus;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantOrderRepositoryPort {
    /** @return false if an entry already exists for the orderId */
    boolean insertIfAbsent(RestaurantOrder order);

    /** Only returns the order if it belongs to the restaurant: foreign and absent orders look the same. */
    Optional<RestaurantOrder> findByIdAndRestaurant(UUID orderId, UUID restaurantId);

    PageResult<RestaurantOrder> list(UUID restaurantId, RestaurantOrderStatus status, int limit, String cursor);

    /**
     * Conditional update (WHERE orderId = ? AND version = expectedVersion): only one concurrent decision succeeds
     * (Spec 3.5 "Concurrency").
     *
     * @return false when the stored version no longer equals expectedVersion
     */
    boolean updateIfVersion(RestaurantOrder updated, int expectedVersion);
}
