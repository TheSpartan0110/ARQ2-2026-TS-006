package com.eatsnow.orders.infrastructure.adapter.out.persistence;

import com.eatsnow.orders.domain.port.out.SentCommandPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PostgresSentCommandAdapter implements SentCommandPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean markSent(UUID orderId, String commandType) {
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO orders.sent_commands (order_id, command_type) VALUES (:orderId, :type)
                ON CONFLICT DO NOTHING
                """)
                .setParameter("orderId", orderId)
                .setParameter("type", commandType)
                .executeUpdate();
        return inserted == 1;
    }
}
