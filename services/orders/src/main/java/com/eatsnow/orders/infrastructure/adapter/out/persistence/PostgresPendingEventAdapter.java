package com.eatsnow.orders.infrastructure.adapter.out.persistence;

import com.eatsnow.orders.domain.model.OrderEvent;
import com.eatsnow.orders.domain.model.PendingEvent;
import com.eatsnow.orders.domain.port.out.PendingEventStorePort;
import com.eatsnow.orders.infrastructure.adapter.out.persistence.entity.PendingEventEntity;
import com.eatsnow.orders.infrastructure.adapter.out.persistence.repository.SpringDataPendingEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Stores premature events as JSON, keyed by the event record's simple name. */
@Component
public class PostgresPendingEventAdapter implements PendingEventStorePort {

    private static final Map<String, Class<? extends OrderEvent>> TYPES = Map.of(
            "RestaurantAccepted", OrderEvent.RestaurantAccepted.class,
            "RestaurantRejected", OrderEvent.RestaurantRejected.class,
            "RestaurantPreparing", OrderEvent.RestaurantPreparing.class,
            "DeliveryAssigned", OrderEvent.DeliveryAssigned.class,
            "DeliveryPickedUp", OrderEvent.DeliveryPickedUp.class,
            "DeliveryCompleted", OrderEvent.DeliveryCompleted.class,
            "PaymentRefunded", OrderEvent.PaymentRefunded.class);

    private final SpringDataPendingEventRepository repository;
    private final ObjectMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresPendingEventAdapter(SpringDataPendingEventRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public void store(OrderEvent event) {
        try {
            repository.save(new PendingEventEntity(event.messageId(), event.orderId(),
                    event.getClass().getSimpleName(), mapper.writeValueAsString(event), "PENDING", Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the pending event", e);
        }
    }

    @Override
    public List<PendingEvent> findPending(UUID orderId) {
        return repository.findPendingByOrder(orderId).stream()
                .map(e -> new PendingEvent(e.getId(), read(e))).toList();
    }

    @Override
    public void delete(long id) {
        repository.deleteById(id);
    }

    @Override
    public void quarantine(long id) {
        repository.findById(id).ifPresent(e -> e.setStatus("QUARANTINED"));
    }

    @Override
    public List<UUID> orderIdsWithPending(int limit) {
        return repository.findOrderIdsWithPending(PageRequest.of(0, limit));
    }

    @Override
    public long oldestPendingSeconds() {
        Object seconds = entityManager.createNativeQuery("""
                SELECT COALESCE(EXTRACT(EPOCH FROM (now() - min(stored_at))), 0)
                FROM orders.pending_events WHERE status = 'PENDING'
                """).getSingleResult();
        return ((Number) seconds).longValue();
    }

    private OrderEvent read(PendingEventEntity entity) {
        Class<? extends OrderEvent> type = TYPES.get(entity.getEventType());
        if (type == null) {
            throw new IllegalStateException("Unknown pending event type " + entity.getEventType());
        }
        try {
            return mapper.readValue(entity.getPayload(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored pending event is corrupt", e);
        }
    }
}
