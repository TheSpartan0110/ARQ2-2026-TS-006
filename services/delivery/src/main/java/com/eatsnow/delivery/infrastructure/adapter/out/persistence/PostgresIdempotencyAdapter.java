package com.eatsnow.delivery.infrastructure.adapter.out.persistence;

import com.eatsnow.common.domain.exception.RequestInProgressException;
import com.eatsnow.common.domain.model.IdempotencyEntry;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.delivery.infrastructure.adapter.out.persistence.entity.IdempotencyRecordEntity;
import com.eatsnow.delivery.infrastructure.adapter.out.persistence.repository.SpringDataIdempotencyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PostgresIdempotencyAdapter implements IdempotencyStoragePort {

    private final SpringDataIdempotencyRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public PostgresIdempotencyAdapter(SpringDataIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<IdempotencyEntry> find(String scopeKey) {
        return repository.findById(scopeKey).map(this::toDomain);
    }

    /**
     * Claims the key with INSERT ... ON CONFLICT DO NOTHING. A concurrent request with the same key waits on the
     * primary key until the first transaction ends; if that one committed, nothing is inserted and this attempt
     * receives 409 REQUEST_IN_PROGRESS with Retry-After and then replays the stored response (Spec 3.1).
     */
    @Override
    public void start(String scopeKey, String requestHash) {
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO deliveries.idempotency_records (scope_key, request_hash, status, created_at)
                VALUES (:scopeKey, :hash, 'IN_PROGRESS', now())
                ON CONFLICT DO NOTHING
                """)
                .setParameter("scopeKey", scopeKey)
                .setParameter("hash", requestHash)
                .executeUpdate();
        if (inserted == 0) {
            throw new RequestInProgressException("A request with this Idempotency-Key is being processed");
        }
    }

    @Override
    public void complete(String scopeKey, String requestHash, int statusCode, String responseBody) {
        repository.save(new IdempotencyRecordEntity(scopeKey, requestHash, "COMPLETED", statusCode, responseBody,
                Instant.now()));
    }

    @Override
    public void release(String scopeKey) {
        repository.deleteById(scopeKey);
    }

    private IdempotencyEntry toDomain(IdempotencyRecordEntity entity) {
        return new IdempotencyEntry(entity.getScopeKey(), entity.getRequestHash(), entity.getStatus(),
                entity.getStatusCode() != null ? entity.getStatusCode() : 0, entity.getResponseBody(),
                entity.getCreatedAt());
    }
}
