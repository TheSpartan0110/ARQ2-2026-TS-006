package com.eatsnow.delivery.domain.model;

/** Delivery lifecycle (Spec 2.1): UNASSIGNED -> ASSIGNED -> PICKED_UP -> DELIVERED. */
public enum DeliveryStatus {
    UNASSIGNED, ASSIGNED, PICKED_UP, DELIVERED
}
