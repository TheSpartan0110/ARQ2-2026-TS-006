package com.eatsnow.catalog.domain.port.in;

import com.eatsnow.catalog.domain.model.MenuItem;
import com.eatsnow.common.domain.model.PageResult;

import java.util.UUID;

public interface GetRestaurantMenuQuery {
    PageResult<MenuItem> getMenu(UUID restaurantId, int limit, String cursor);
}
