package com.eatsnow.orders.infrastructure.adapter.out.persistence;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.orders.domain.exception.QuoteAlreadyUsedException;
import com.eatsnow.orders.domain.model.DeliveryAddress;
import com.eatsnow.orders.domain.model.Order;
import com.eatsnow.orders.domain.model.OrderItem;
import com.eatsnow.orders.domain.model.OrderStateChange;
import com.eatsnow.orders.domain.model.OrderStatus;
import com.eatsnow.orders.domain.model.PaymentStatus;
import com.eatsnow.orders.domain.port.out.OrderRepositoryPort;
import com.eatsnow.orders.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.eatsnow.orders.infrastructure.adapter.out.persistence.entity.OrderStatusHistoryEntity;
import com.eatsnow.orders.infrastructure.adapter.out.persistence.repository.SpringDataOrderHistoryRepository;
import com.eatsnow.orders.infrastructure.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class PostgresOrderRepositoryAdapter implements OrderRepositoryPort {

    private final SpringDataOrderRepository orders;
    private final SpringDataOrderHistoryRepository history;
    private final ObjectMapper mapper;

    public PostgresOrderRepositoryAdapter(SpringDataOrderRepository orders, SpringDataOrderHistoryRepository history,
                                          ObjectMapper mapper) {
        this.orders = orders;
        this.history = history;
        this.mapper = mapper;
    }

    @Override
    public void insert(Order order) {
        try {
            orders.saveAndFlush(toEntity(order));            // flush now so the unique quote constraint fires here
        } catch (DataIntegrityViolationException e) {
            throw new QuoteAlreadyUsedException();
        }
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        return orders.findById(orderId).map(this::toDomain);
    }

    @Override
    public Optional<Order> findByIdForUpdate(UUID orderId) {
        return orders.findByIdForUpdate(orderId).map(this::toDomain);
    }

    @Override
    public void update(Order order) {
        // The entity is already managed (loaded with the lock in this transaction): dirty checking writes it.
        OrderEntity entity = orders.findById(order.id()).orElseThrow();
        entity.setStatus(order.status().name());
        entity.setPaymentStatus(order.paymentStatus().name());
        entity.setPaymentId(order.paymentId());
        entity.setDeliveryId(order.deliveryId());
        entity.setDriverId(order.driverId());
        entity.setVersion(order.version());
        entity.setUpdatedAt(order.updatedAt());
    }

    @Override
    public void appendHistory(OrderStateChange change) {
        history.save(new OrderStatusHistoryEntity(change.orderId(),
                change.fromStatus() != null ? change.fromStatus().name() : null, change.toStatus().name(),
                change.fromPaymentStatus() != null ? change.fromPaymentStatus().name() : null,
                change.toPaymentStatus().name(), change.orderVersion(), change.causedBy(), Instant.now()));
    }

    private OrderEntity toEntity(Order o) {
        return new OrderEntity(o.id(), o.customerId(), o.restaurantId(), o.quoteId(), toJson(o.items()),
                o.total().amountMinor(), o.total().currency(), toJson(o.deliveryAddress()), o.status().name(),
                o.paymentStatus().name(), o.correlationId(), o.paymentId(), o.deliveryId(), o.driverId(), o.version(),
                o.createdAt(), o.updatedAt());
    }

    private Order toDomain(OrderEntity e) {
        return new Order(e.getId(), e.getCustomerId(), e.getRestaurantId(), e.getQuoteId(),
                fromJson(e.getItems(), new TypeReference<List<OrderItem>>() { }),
                new Money(e.getTotalAmountMinor(), e.getCurrency()),
                fromJson(e.getDeliveryAddress(), new TypeReference<DeliveryAddress>() { }),
                OrderStatus.valueOf(e.getStatus()), PaymentStatus.valueOf(e.getPaymentStatus()),
                e.getCorrelationId(), e.getPaymentId(), e.getDeliveryId(), e.getDriverId(), e.getVersion(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize order snapshot", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored order JSON is corrupt", e);
        }
    }
}
