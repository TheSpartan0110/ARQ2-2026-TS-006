package com.eatsnow.notifications.domain.port.in;

import java.util.UUID;

/** OrderStatusChanged consumer (Spec 4.2.4): records at most one PENDING notification per (orderId, trigger). */
public interface ProcessOrderStatusChangeUseCase {

    record Change(UUID orderId, UUID customerId, int orderVersion, String previousStatus, String status,
                  String paymentStatus, UUID correlationId) { }

    void process(Change change);
}
