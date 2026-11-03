package com.eatsnow.notifications.infrastructure.adapter.out.persistence;

import com.eatsnow.notifications.domain.port.out.OrderVersionPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PostgresOrderVersionAdapter implements OrderVersionPort {

    @PersistenceContext
    private EntityManager entityManager;

    /** Inserts the order or raises its highest version only if the new one is greater (0 rows when stale). */
    @Override
    public boolean advance(UUID orderId, int version) {
        int changed = entityManager.createNativeQuery("""
                INSERT INTO notifications.order_versions AS ov (order_id, highest_version) VALUES (:orderId, :version)
                ON CONFLICT (order_id) DO UPDATE
                   SET highest_version = EXCLUDED.highest_version, updated_at = now()
                 WHERE ov.highest_version < EXCLUDED.highest_version
                """)
                .setParameter("orderId", orderId)
                .setParameter("version", version)
                .executeUpdate();
        return changed == 1;
    }
}
