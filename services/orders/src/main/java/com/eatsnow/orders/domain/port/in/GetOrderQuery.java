package com.eatsnow.orders.domain.port.in;

import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.orders.domain.model.Order;
import com.eatsnow.orders.domain.model.OrderHistoryEntry;
import com.eatsnow.orders.domain.model.PaymentSummary;
import java.util.List;
import java.util.UUID;

/**
 * GET /orders, /orders/{id}, /orders/{id}/payment and /orders/{id}/history (Spec 3.4). Absent or foreign orders
 * are 404.
 */
public interface GetOrderQuery {
    Order getOrder(UUID customerId, UUID orderId);

    PaymentSummary getPayment(UUID customerId, UUID orderId);

    /** The customer's own orders, newest first. */
    PageResult<Order> listOrders(UUID customerId, int limit, String cursor);

    /** Every recorded status and payment-status change of the order, oldest first. */
    List<OrderHistoryEntry> getHistory(UUID customerId, UUID orderId);
}
