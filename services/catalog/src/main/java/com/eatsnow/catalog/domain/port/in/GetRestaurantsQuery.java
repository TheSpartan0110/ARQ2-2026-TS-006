package com.eatsnow.catalog.domain.port.in;

import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.catalog.domain.model.Restaurant;

public interface GetRestaurantsQuery {
    PageResult<Restaurant> getRestaurants(int limit, String cursor);
}
