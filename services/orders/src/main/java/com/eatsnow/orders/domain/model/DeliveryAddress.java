package com.eatsnow.orders.domain.model;

/** Delivery address snapshot (Spec 2.1 Address). */
public record DeliveryAddress(String line1, String city, String instructions) {
}
