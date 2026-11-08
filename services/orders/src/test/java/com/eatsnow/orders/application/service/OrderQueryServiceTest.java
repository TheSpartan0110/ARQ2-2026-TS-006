package com.eatsnow.orders.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.model.Money;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.orders.domain.model.DeliveryAddress;
import com.eatsnow.orders.domain.model.Order;
import com.eatsnow.orders.domain.model.OrderHistoryEntry;
import com.eatsnow.orders.domain.model.OrderItem;
import com.eatsnow.orders.domain.model.OrderStatus;
import com.eatsnow.orders.domain.model.PaymentStatus;
import com.eatsnow.orders.domain.port.out.OrderRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private static final Money TOTAL = new Money(2500000, "COP");

    @Mock
    private OrderRepositoryPort orders;

    @InjectMocks
    private OrderQueryService service;

    private Order orderOf(UUID customerId) {
        return new Order(ORDER_ID, customerId, UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderItem(UUID.randomUUID(), "Classic Burger", 1, TOTAL)), TOTAL,
                new DeliveryAddress("123 Example Street", "Medellin", null), OrderStatus.ACCEPTED,
                PaymentStatus.AUTHORIZED, UUID.randomUUID(), null, null, null, 3, Instant.now(), Instant.now());
    }

    @Test
    void historyOfOwnOrderIsReturnedOldestFirst() {
        List<OrderHistoryEntry> history = List.of(
                new OrderHistoryEntry(null, OrderStatus.CREATED, null, PaymentStatus.PENDING, 1, null, Instant.now()),
                new OrderHistoryEntry(OrderStatus.CREATED, OrderStatus.CREATED, PaymentStatus.PENDING,
                        PaymentStatus.AUTHORIZED, 2, UUID.randomUUID(), Instant.now()));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(ALEX)));
        when(orders.findHistory(ORDER_ID)).thenReturn(history);

        assertEquals(history, service.getHistory(ALEX, ORDER_ID));
    }

    @Test
    void historyOfAnotherCustomersOrderIsNotFound() {
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(orderOf(UUID.randomUUID())));

        assertThrows(EntityNotFoundException.class, () -> service.getHistory(ALEX, ORDER_ID));
        verify(orders, never()).findHistory(ORDER_ID);
    }

    @Test
    void listLimitIsBoundedBetweenOneAndOneHundred() {
        PageResult<Order> empty = new PageResult<>(List.of(), null);
        when(orders.findByCustomer(ALEX, 100, null)).thenReturn(empty);
        when(orders.findByCustomer(ALEX, 1, null)).thenReturn(empty);

        service.listOrders(ALEX, 500, null);
        service.listOrders(ALEX, 0, null);

        verify(orders).findByCustomer(ALEX, 100, null);
        verify(orders).findByCustomer(ALEX, 1, null);
    }
}
