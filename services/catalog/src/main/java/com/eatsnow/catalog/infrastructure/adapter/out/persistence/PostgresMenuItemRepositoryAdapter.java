package com.eatsnow.catalog.infrastructure.adapter.out.persistence;

import com.eatsnow.catalog.domain.model.MenuItem;
import com.eatsnow.common.domain.model.Money;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.catalog.domain.port.out.MenuItemRepositoryPort;
import com.eatsnow.catalog.infrastructure.adapter.out.persistence.entity.MenuItemEntity;
import com.eatsnow.catalog.infrastructure.adapter.out.persistence.repository.SpringDataMenuItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostgresMenuItemRepositoryAdapter implements MenuItemRepositoryPort {

    private final SpringDataMenuItemRepository repository;

    public PostgresMenuItemRepositoryAdapter(SpringDataMenuItemRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<MenuItem> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public PageResult<MenuItem> findByRestaurantId(UUID restaurantId, int limit, String cursor) {
        int offset = decodeCursor(cursor);
        int pageIndex = offset / limit;

        List<MenuItem> items = repository.findByRestaurantIdOrderByNameAsc(restaurantId, PageRequest.of(pageIndex, limit))
                .stream()
                .map(this::toDomain)
                .toList();

        String nextCursor = null;
        if (items.size() == limit) {
            nextCursor = encodeCursor(offset + limit);
        }

        return new PageResult<>(items, nextCursor);
    }

    @Override
    public List<MenuItem> findByIds(List<UUID> ids) {
        return repository.findByIdIn(ids)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private MenuItem toDomain(MenuItemEntity entity) {
        return new MenuItem(
                entity.getId(),
                entity.getRestaurantId(),
                entity.getName(),
                new Money(entity.getAmountMinor(), entity.getCurrency()),
                entity.isAvailable(),
                entity.getVersion()
        );
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
