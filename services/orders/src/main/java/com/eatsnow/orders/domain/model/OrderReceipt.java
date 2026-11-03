package com.eatsnow.orders.domain.model;

import java.util.UUID;

/** OrderReceipt (Spec 3.4). */
public record OrderReceipt(UUID orderId, String status, UUID correlationId) {
}
