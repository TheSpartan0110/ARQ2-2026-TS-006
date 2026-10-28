package com.eatsnow.identity.infrastructure.adapter.out.persistence;

import com.eatsnow.common.domain.model.IdempotencyEntry;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.identity.infrastructure.adapter.out.persistence.entity.IdempotencyRecordEntity;
import com.eatsnow.identity.infrastructure.adapter.out.persistence.repository.SpringDataIdempotencyRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class PostgresIdempotencyAdapter implements IdempotencyStoragePort {

    private final SpringDataIdempotencyRepository repository;

    public PostgresIdempotencyAdapter(SpringDataIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<IdempotencyEntry> find(String scopeKey) {
        return repository.findById(scopeKey).map(this::toDomain);
    }

    @Override
    public void start(String scopeKey, String requestHash) {
        IdempotencyRecordEntity entity = new IdempotencyRecordEntity(
                scopeKey,
                requestHash,
                "IN_PROGRESS",
                null,
                null,
                Instant.now()
        );
        repository.save(entity);
    }

    @Override
    public void complete(String scopeKey, String requestHash, int statusCode, String responseBody) {
        IdempotencyRecordEntity entity = new IdempotencyRecordEntity(
                scopeKey,
                requestHash,
                "COMPLETED",
                statusCode,
                responseBody,
                Instant.now()
        );
        repository.save(entity);
    }

    @Override
    public void release(String scopeKey) {
        repository.deleteById(scopeKey);
    }

    private IdempotencyEntry toDomain(IdempotencyRecordEntity entity) {
        return new IdempotencyEntry(
                entity.getScopeKey(),
                entity.getRequestHash(),
                entity.getStatus(),
                entity.getStatusCode() != null ? entity.getStatusCode() : 0,
                entity.getResponseBody(),
                entity.getCreatedAt()
        );
    }
}
