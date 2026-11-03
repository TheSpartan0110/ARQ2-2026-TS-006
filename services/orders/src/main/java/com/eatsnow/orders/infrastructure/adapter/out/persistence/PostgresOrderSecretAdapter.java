package com.eatsnow.orders.infrastructure.adapter.out.persistence;

import com.eatsnow.orders.domain.port.out.OrderSecretPort;
import com.eatsnow.orders.infrastructure.adapter.out.persistence.entity.OrderSecretEntity;
import com.eatsnow.orders.infrastructure.adapter.out.persistence.repository.SpringDataOrderSecretRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PostgresOrderSecretAdapter implements OrderSecretPort {

    private final SpringDataOrderSecretRepository repository;

    public PostgresOrderSecretAdapter(SpringDataOrderSecretRepository repository) {
        this.repository = repository;
    }

    @Override
    public void store(UUID orderId, String encryptedPaymentToken) {
        repository.save(new OrderSecretEntity(orderId, encryptedPaymentToken, Instant.now()));
    }

    @Override
    public Optional<String> find(UUID orderId) {
        return repository.findById(orderId).map(OrderSecretEntity::getEncryptedPaymentToken);
    }

    @Override
    public void delete(UUID orderId) {
        repository.deleteById(orderId);
    }
}
