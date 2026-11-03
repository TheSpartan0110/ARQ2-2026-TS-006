package com.eatsnow.orders.domain.model;

/** Global order states (Spec 2.1). Only CREATED and PAYMENT_FAILED are reachable in this step. */
public enum OrderStatus {
    CREATED, ACCEPTED, PREPARING, ON_THE_WAY, DELIVERED, REJECTED, PAYMENT_FAILED
}
