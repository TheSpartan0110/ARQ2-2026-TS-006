package com.eatsnow.orders.domain.port.in;

import com.eatsnow.orders.domain.model.Order;
import com.eatsnow.orders.domain.model.PaymentSummary;
import java.util.UUID;

/** GET /orders/{id} and GET /orders/{id}/payment (Spec 3.4). Absent or foreign orders are 404. */
public interface GetOrderQuery {
    Order getOrder(UUID customerId, UUID orderId);

    PaymentSummary getPayment(UUID customerId, UUID orderId);
}
