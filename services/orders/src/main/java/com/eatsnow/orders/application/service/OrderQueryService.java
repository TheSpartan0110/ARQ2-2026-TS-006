package com.eatsnow.orders.application.service;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.orders.domain.model.Order;
import com.eatsnow.orders.domain.model.PaymentSummary;
import com.eatsnow.orders.domain.port.in.GetOrderQuery;
import com.eatsnow.orders.domain.port.out.OrderRepositoryPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService implements GetOrderQuery {
    private final OrderRepositoryPort orders;

    public OrderQueryService(OrderRepositoryPort orders) {
        this.orders = orders;
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(UUID customerId, UUID orderId) {
        // Absent and foreign orders are indistinguishable: 404 prevents enumeration (Spec 3.1)
        return orders.findById(orderId)
                .filter(order -> order.customerId().equals(customerId))
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSummary getPayment(UUID customerId, UUID orderId) {
        Order order = getOrder(customerId, orderId);
        return new PaymentSummary(order.id(), order.paymentStatus(), order.total(), order.updatedAt());
    }
}
