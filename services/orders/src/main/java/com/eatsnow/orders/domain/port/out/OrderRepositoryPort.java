package com.eatsnow.orders.domain.port.out;

import com.eatsnow.orders.domain.model.Order;
import com.eatsnow.orders.domain.model.OrderStateChange;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryPort {
    /** @throws com.eatsnow.orders.domain.exception.QuoteAlreadyUsedException if the quote already has an order */
    void insert(Order order);

    Optional<Order> findById(UUID orderId);

    /** Loads the order with a row lock: serializes every state write of one order (Spec 4.3 item 3). */
    Optional<Order> findByIdForUpdate(UUID orderId);

    void update(Order order);

    void appendHistory(OrderStateChange change);
}
