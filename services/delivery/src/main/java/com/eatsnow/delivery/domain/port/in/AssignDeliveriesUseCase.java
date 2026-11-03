package com.eatsnow.delivery.domain.port.in;

/** Automatic assignment of UNASSIGNED deliveries to assignable drivers (Spec 3.6). Driven by the system, not by HTTP. */
public interface AssignDeliveriesUseCase {
    /** One pass over the UNASSIGNED deliveries, oldest first. Each delivery is its own transaction. */
    int assignPending();

    /** Age in seconds of the oldest UNASSIGNED delivery, 0 if none (feeds the DeliveryUnassigned alert). */
    long oldestUnassignedSeconds();
}
