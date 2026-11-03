package com.eatsnow.restaurants.infrastructure.adapter.out.persistence;

import com.eatsnow.common.domain.model.KeysetCursor;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.restaurants.domain.model.OrderLine;
import com.eatsnow.restaurants.domain.model.RestaurantOrder;
import com.eatsnow.restaurants.domain.model.RestaurantOrderStatus;
import com.eatsnow.restaurants.domain.port.out.RestaurantOrderRepositoryPort;
import com.eatsnow.restaurants.infrastructure.adapter.out.persistence.entity.RestaurantOrderEntity;
import com.eatsnow.restaurants.infrastructure.adapter.out.persistence.repository.SpringDataRestaurantOrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresRestaurantOrderRepositoryAdapter implements RestaurantOrderRepositoryPort {

    private final SpringDataRestaurantOrderRepository repository;
    private final ObjectMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresRestaurantOrderRepositoryAdapter(SpringDataRestaurantOrderRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public boolean insertIfAbsent(RestaurantOrder o) {
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO restaurants.restaurant_orders (order_id, restaurant_id, items, status, reason,
                                                           correlation_id, version, created_at, updated_at)
                VALUES (:orderId, :restaurantId, :items, 'PENDING', NULL, :correlationId, 1, now(), now())
                ON CONFLICT (order_id) DO NOTHING
                """)
                .setParameter("orderId", o.orderId())
                .setParameter("restaurantId", o.restaurantId())
                .setParameter("items", toJson(o.items()))
                .setParameter("correlationId", o.correlationId())
                .executeUpdate();
        return inserted == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RestaurantOrder> findByIdAndRestaurant(UUID orderId, UUID restaurantId) {
        return repository.findByOrderIdAndRestaurantId(orderId, restaurantId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RestaurantOrder> list(UUID restaurantId, RestaurantOrderStatus status, int limit, String cursor) {
        KeysetCursor from = KeysetCursor.decode(cursor);
        PageRequest page = PageRequest.of(0, limit + 1);          // one extra row tells whether another page exists
        List<RestaurantOrderEntity> rows = status == null
                ? repository.findPage(restaurantId, from.createdAt(), from.id(), page)
                : repository.findPageByStatus(restaurantId, status.name(), from.createdAt(), from.id(), page);
        boolean hasMore = rows.size() > limit;
        List<RestaurantOrderEntity> visible = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore
                ? new KeysetCursor(visible.get(visible.size() - 1).getCreatedAt(),
                        visible.get(visible.size() - 1).getOrderId()).encode()
                : null;
        return new PageResult<>(visible.stream().map(this::toDomain).toList(), next);
    }

    @Override
    @Transactional
    public boolean updateIfVersion(RestaurantOrder u, int expectedVersion) {
        return repository.updateIfVersion(u.orderId(), expectedVersion, u.status().name(), u.reason(), u.version(),
                u.updatedAt()) == 1;
    }

    private RestaurantOrder toDomain(RestaurantOrderEntity e) {
        try {
            return new RestaurantOrder(e.getOrderId(), e.getRestaurantId(),
                    mapper.readValue(e.getItems(), new TypeReference<List<OrderLine>>() { }),
                    RestaurantOrderStatus.valueOf(e.getStatus()), e.getReason(), e.getCorrelationId(),
                    e.getVersion(), e.getCreatedAt(), e.getUpdatedAt());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored restaurant order items are corrupt", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the order items", e);
        }
    }
}
