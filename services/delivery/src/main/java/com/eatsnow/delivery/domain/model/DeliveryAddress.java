package com.eatsnow.delivery.domain.model;

/** Delivery address snapshot. Sensitive: only the assigned driver may see it and it is never logged (Spec 4.2.5). */
public record DeliveryAddress(String line1, String city, String instructions) {
}
