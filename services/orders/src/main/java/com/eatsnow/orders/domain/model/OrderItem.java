package com.eatsnow.orders.domain.model;

import com.eatsnow.common.domain.model.Money;
import java.util.UUID;

/** Immutable snapshot of a purchased item, copied from the verified quote (Spec 2.2). */
public record OrderItem(UUID menuItemId, String name, int quantity, Money unitPrice) {
    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }
}
