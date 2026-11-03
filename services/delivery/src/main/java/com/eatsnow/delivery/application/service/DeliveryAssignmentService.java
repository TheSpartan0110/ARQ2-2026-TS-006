package com.eatsnow.delivery.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.delivery.domain.port.in.AssignDeliveriesUseCase;
import com.eatsnow.delivery.domain.port.out.DeliveryRepositoryPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One assignment pass, oldest delivery first (first-come, first-served by createdAt, Spec 3.6). Not transactional:
 * each delivery runs in its own transaction inside DeliveryAssigner, so one failure never blocks the others.
 */
@Service
public class DeliveryAssignmentService implements AssignDeliveriesUseCase {
    private static final Logger log = LoggerFactory.getLogger(DeliveryAssignmentService.class);
    private static final int BATCH = 50;

    private final DeliveryRepositoryPort deliveries;
    private final DeliveryAssigner assigner;

    public DeliveryAssignmentService(DeliveryRepositoryPort deliveries, DeliveryAssigner assigner) {
        this.deliveries = deliveries;
        this.assigner = assigner;
    }

    @Override
    public int assignPending() {
        int assigned = 0;
        for (UUID deliveryId : deliveries.findUnassignedIds(BATCH)) {
            try {
                if (assigner.assignOne(deliveryId)) {
                    assigned++;
                }
            } catch (RuntimeException e) {
                log.warn("Assignment attempt failed, will retry", kv("operation", "assign delivery"),
                        kv("outcome", "RETRY"), e);
            }
        }
        return assigned;
    }

    @Override
    public long oldestUnassignedSeconds() {
        return deliveries.oldestUnassignedSeconds();
    }
}
