package com.eatsnow.catalog.infrastructure.adapter.out.persistence;

import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.catalog.domain.model.Restaurant;
import com.eatsnow.catalog.domain.port.out.RestaurantRepositoryPort;
import com.eatsnow.catalog.infrastructure.adapter.out.persistence.entity.RestaurantEntity;
import com.eatsnow.catalog.infrastructure.adapter.out.persistence.repository.SpringDataRestaurantRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostgresRestaurantRepositoryAdapter implements RestaurantRepositoryPort {

    private final SpringDataRestaurantRepository repository;

    public PostgresRestaurantRepositoryAdapter(SpringDataRestaurantRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Restaurant> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public PageResult<Restaurant> findAll(int limit, String cursor) {
        int offset = decodeCursor(cursor);
        int pageIndex = offset / limit;

        List<Restaurant> items = repository.findByOrderByNameAsc(PageRequest.of(pageIndex, limit))
                .stream()
                .map(this::toDomain)
                .toList();

        String nextCursor = null;
        if (items.size() == limit) {
            nextCursor = encodeCursor(offset + limit);
        }

        return new PageResult<>(items, nextCursor);
    }

    private Restaurant toDomain(RestaurantEntity entity) {
        return new Restaurant(entity.getId(), entity.getName(), entity.isAvailable());
    }

    private int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Math.max(0, Integer.parseInt(decoded));
        } catch (Exception e) {
            return 0;
        }
    }

    private String encodeCursor(int nextOffset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(nextOffset).getBytes(StandardCharsets.UTF_8));
    }
}
