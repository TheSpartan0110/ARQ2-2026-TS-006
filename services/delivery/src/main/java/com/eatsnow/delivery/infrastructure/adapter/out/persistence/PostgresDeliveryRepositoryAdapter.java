package com.eatsnow.delivery.infrastructure.adapter.out.persistence;

import com.eatsnow.common.domain.model.KeysetCursor;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.delivery.domain.model.Delivery;
import com.eatsnow.delivery.domain.model.DeliveryAddress;
import com.eatsnow.delivery.domain.model.DeliveryStatus;
import com.eatsnow.delivery.domain.port.out.DeliveryRepositoryPort;
import com.eatsnow.delivery.infrastructure.adapter.out.persistence.entity.DeliveryEntity;
import com.eatsnow.delivery.infrastructure.adapter.out.persistence.repository.SpringDataDeliveryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
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
public class PostgresDeliveryRepositoryAdapter implements DeliveryRepositoryPort {

    private final SpringDataDeliveryRepository repository;
    private final ObjectMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresDeliveryRepositoryAdapter(SpringDataDeliveryRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public boolean insertIfAbsent(Delivery d) {
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO deliveries.deliveries (id, order_id, restaurant_id, driver_id, delivery_address, status,
                                                   correlation_id, command_message_id, version, created_at, updated_at)
                VALUES (:id, :orderId, :restaurantId, NULL, :address, 'UNASSIGNED', :correlationId, :commandId, 1,
                        now(), now())
                ON CONFLICT (order_id) DO NOTHING
                """)
                .setParameter("id", d.id())
                .setParameter("orderId", d.orderId())
                .setParameter("restaurantId", d.restaurantId())
                .setParameter("address", toJson(d.deliveryAddress()))
                .setParameter("correlationId", d.correlationId())
                .setParameter("commandId", d.commandMessageId())
                .executeUpdate();
        return inserted == 1;
    }

    @Override
    @Transactional
    public Optional<Delivery> findByIdForUpdate(UUID deliveryId) {
        return repository.findByIdForUpdate(deliveryId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Delivery> findByIdAndDriver(UUID deliveryId, UUID driverId) {
        return repository.findByIdAndDriverId(deliveryId, driverId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Delivery> list(UUID driverId, DeliveryStatus status, int limit, String cursor) {
        KeysetCursor from = KeysetCursor.decode(cursor);
        PageRequest page = PageRequest.of(0, limit + 1);          // one extra row tells whether another page exists
        List<DeliveryEntity> rows = status == null
                ? repository.findPage(driverId, from.createdAt(), from.id(), page)
                : repository.findPageByStatus(driverId, status.name(), from.createdAt(), from.id(), page);
        boolean hasMore = rows.size() > limit;
        List<DeliveryEntity> visible = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore
                ? new KeysetCursor(visible.get(visible.size() - 1).getCreatedAt(),
                        visible.get(visible.size() - 1).getId()).encode()
                : null;
        return new PageResult<>(visible.stream().map(this::toDomain).toList(), next);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findUnassignedIds(int limit) {
        return repository.findUnassignedIds(PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public boolean updateIfVersion(Delivery u, int expectedVersion) {
        return repository.updateIfVersion(u.id(), expectedVersion, u.status().name(), u.driverId(), u.version(),
                u.updatedAt()) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public long oldestUnassignedSeconds() {
        Object seconds = entityManager.createNativeQuery("""
                SELECT COALESCE(EXTRACT(EPOCH FROM (now() - min(created_at))), 0)
                FROM deliveries.deliveries WHERE status = 'UNASSIGNED'
                """).getSingleResult();
        return ((Number) seconds).longValue();
    }

    private Delivery toDomain(DeliveryEntity e) {
        try {
            return new Delivery(e.getId(), e.getOrderId(), e.getRestaurantId(), e.getDriverId(),
                    mapper.readValue(e.getDeliveryAddress(), DeliveryAddress.class), DeliveryStatus.valueOf(e.getStatus()),
                    e.getCorrelationId(), e.getCommandMessageId(), e.getVersion(), e.getCreatedAt(), e.getUpdatedAt());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored delivery address is corrupt", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the delivery address", e);
        }
    }
}
