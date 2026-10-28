package com.eatsnow.identity.infrastructure.adapter.out.persistence;

import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import com.eatsnow.identity.infrastructure.adapter.out.persistence.entity.ProfileEntity;
import com.eatsnow.identity.infrastructure.adapter.out.persistence.repository.SpringDataProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PostgresProfileRepositoryAdapter implements ProfileRepositoryPort {

    private final SpringDataProfileRepository repository;

    public PostgresProfileRepositoryAdapter(SpringDataProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Profile> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Profile> findByEmail(String email) {
        return repository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public Profile save(Profile profile) {
        ProfileEntity entity = toEntity(profile);
        ProfileEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public PageResult<Profile> findAll(int limit, String cursor, Role roleFilter) {
        UUID cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                cursorId = UUID.fromString(cursor.trim());
            } catch (IllegalArgumentException ignored) {
                // Invalid cursor treated as starting from first
            }
        }

        String roleStr = roleFilter != null ? roleFilter.name() : null;
        // Fetch limit + 1 to detect if next page exists
        List<ProfileEntity> entities = repository.findWithCursorAndRole(
                roleStr,
                cursorId,
                PageRequest.of(0, limit + 1)
        );

        boolean hasMore = entities.size() > limit;
        List<ProfileEntity> pageEntities = hasMore ? entities.subList(0, limit) : entities;

        List<Profile> items = pageEntities.stream().map(this::toDomain).toList();
        String nextCursor = hasMore && !items.isEmpty() ? items.get(items.size() - 1).id().toString() : null;

        return new PageResult<>(items, nextCursor);
    }

    private Profile toDomain(ProfileEntity entity) {
        Set<Role> roles = Arrays.stream(entity.getRoles().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Role::valueOf)
                .collect(Collectors.toSet());

        return new Profile(
                entity.getId(),
                entity.getDisplayName(),
                entity.getEmail(),
                roles,
                entity.getRestaurantId(),
                entity.getDriverId(),
                entity.getPasswordHash(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ProfileEntity toEntity(Profile profile) {
        String rolesStr = profile.roles().stream()
                .map(Role::name)
                .collect(Collectors.joining(","));

        return new ProfileEntity(
                profile.id(),
                profile.displayName(),
                profile.email(),
                rolesStr,
                profile.restaurantId(),
                profile.driverId(),
                profile.passwordHash(),
                profile.active(),
                profile.createdAt(),
                profile.updatedAt()
        );
    }
}
