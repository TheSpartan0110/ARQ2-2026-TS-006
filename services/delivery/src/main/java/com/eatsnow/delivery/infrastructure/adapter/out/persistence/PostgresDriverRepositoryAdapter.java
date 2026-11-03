package com.eatsnow.delivery.infrastructure.adapter.out.persistence;

import com.eatsnow.delivery.domain.model.Driver;
import com.eatsnow.delivery.domain.port.out.DriverRepositoryPort;
import com.eatsnow.delivery.infrastructure.adapter.out.persistence.entity.DriverEntity;
import com.eatsnow.delivery.infrastructure.adapter.out.persistence.repository.SpringDataDriverRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresDriverRepositoryAdapter implements DriverRepositoryPort {

    private final SpringDataDriverRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresDriverRepositoryAdapter(SpringDataDriverRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean insertIfAbsent(Driver d) {
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO deliveries.drivers (id, profile_id, on_duty, active_delivery_id, last_assigned_at, version)
                VALUES (:id, :profileId, :onDuty, NULL, NULL, 1)
                ON CONFLICT (id) DO NOTHING
                """)
                .setParameter("id", d.id())
                .setParameter("profileId", d.profileId())
                .setParameter("onDuty", d.onDuty())
                .executeUpdate();
        return inserted == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Driver> findById(UUID driverId) {
        return repository.findById(driverId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Driver> findAssignable(int limit) {
        return repository.findAssignable(PageRequest.of(0, limit)).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean claim(UUID driverId, int expectedVersion, UUID deliveryId) {
        return repository.claim(driverId, expectedVersion, deliveryId, Instant.now()) == 1;
    }

    @Override
    @Transactional
    public void release(UUID driverId, UUID deliveryId) {
        repository.release(driverId, deliveryId, Instant.now());
    }

    @Override
    @Transactional
    public boolean updateDuty(UUID driverId, int expectedVersion, boolean onDuty) {
        return repository.updateDuty(driverId, expectedVersion, onDuty, Instant.now()) == 1;
    }

    private Driver toDomain(DriverEntity e) {
        return new Driver(e.getId(), e.getProfileId(), e.isOnDuty(), e.getActiveDeliveryId(), e.getLastAssignedAt(),
                e.getVersion());
    }
}
