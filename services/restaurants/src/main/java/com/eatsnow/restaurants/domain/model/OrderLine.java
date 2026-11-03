package com.eatsnow.restaurants.domain.model;

import com.eatsnow.common.domain.model.Money;
import java.util.UUID;

/** Item snapshot received with RequestRestaurantAcceptance (Spec 2.2 OrderItem). */
public record OrderLine(UUID menuItemId, String name, int quantity, Money unitPrice) {
}
