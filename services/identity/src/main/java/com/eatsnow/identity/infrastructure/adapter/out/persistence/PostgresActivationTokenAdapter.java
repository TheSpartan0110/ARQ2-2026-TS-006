package com.eatsnow.identity.infrastructure.adapter.out.persistence;

import com.eatsnow.identity.domain.port.out.ActivationTokenRepositoryPort;
import com.eatsnow.identity.infrastructure.adapter.out.persistence.entity.ActivationTokenEntity;
import com.eatsnow.identity.infrastructure.adapter.out.persistence.repository.SpringDataActivationTokenRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostgresActivationTokenAdapter implements ActivationTokenRepositoryPort {

    private final SpringDataActivationTokenRepository repository;

    public PostgresActivationTokenAdapter(SpringDataActivationTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(String tokenHash, UUID profileId, Instant expiresAt) {
        repository.save(new ActivationTokenEntity(tokenHash, profileId, expiresAt, Instant.now()));
    }

    @Override
    public Optional<UUID> findUsableProfileId(String tokenHash, Instant now) {
        return repository.findUsable(tokenHash, now).map(ActivationTokenEntity::getProfileId);
    }

    @Override
    public boolean markUsed(String tokenHash, Instant now) {
        return repository.markUsed(tokenHash, now) == 1;
    }
}
