package com.eatsnow.catalog.domain.port.out;

import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.catalog.domain.model.Restaurant;

import java.util.Optional;
import java.util.UUID;

public interface RestaurantRepositoryPort {
    Optional<Restaurant> findById(UUID id);
    PageResult<Restaurant> findAll(int limit, String cursor);
}
