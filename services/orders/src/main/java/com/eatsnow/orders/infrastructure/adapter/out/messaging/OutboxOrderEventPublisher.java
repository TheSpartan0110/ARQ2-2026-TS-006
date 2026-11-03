package com.eatsnow.orders.infrastructure.adapter.out.messaging;

import com.eatsnow.messaging.contract.Payloads;
import com.eatsnow.messaging.outbox.OutboxWriter;
import com.eatsnow.messaging.support.MessageTypes;
import com.eatsnow.orders.domain.model.Order;
import com.eatsnow.orders.domain.model.OrderStatus;
import com.eatsnow.orders.domain.port.out.OrderEventPublisherPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Writes messages to the transactional outbox (ADR 001). The relay publishes them after the commit. */
@Component
public class OutboxOrderEventPublisher implements OrderEventPublisherPort {
    private static final String PRODUCER = "orders";

    private final OutboxWriter outbox;

    public OutboxOrderEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void orderCreated(Order order) {
        outbox.enqueue(MessageTypes.EVENTS, MessageTypes.ORDER_CREATED, PRODUCER, order.id(), order.correlationId(),
                null, order.id(), order.version(),
                new Payloads.OrderCreated(order.customerId(), order.restaurantId(), items(order), order.total()));
    }

    @Override
    public void orderStatusChanged(Order order, OrderStatus previousStatus, UUID causationId) {
        outbox.enqueue(MessageTypes.EVENTS, MessageTypes.ORDER_STATUS_CHANGED, PRODUCER, order.id(),
                order.correlationId(), causationId, order.id(), order.version(),
                new Payloads.OrderStatusChanged(order.customerId(),
                        previousStatus != null ? previousStatus.name() : null,
                        order.status().name(), order.paymentStatus().name()));
    }

    @Override
    public void chargePayment(Order order, String encryptedPaymentToken, UUID causationId) {
        outbox.enqueue(MessageTypes.COMMANDS, MessageTypes.CHARGE_PAYMENT, PRODUCER, order.id(),
                order.correlationId(), causationId, order.id(), order.version(),
                new Payloads.ChargePayment(order.total(), encryptedPaymentToken));
    }

    @Override
    public void requestRestaurantAcceptance(Order order, UUID causationId) {
        outbox.enqueue(MessageTypes.COMMANDS, MessageTypes.REQUEST_RESTAURANT_ACCEPTANCE, PRODUCER, order.id(),
                order.correlationId(), causationId, order.id(), order.version(),
                new Payloads.RequestRestaurantAcceptance(order.restaurantId(), items(order)));
    }

    @Override
    public void refundPayment(Order order, UUID causationId) {
        outbox.enqueue(MessageTypes.COMMANDS, MessageTypes.REFUND_PAYMENT, PRODUCER, order.id(),
                order.correlationId(), causationId, order.id(), order.version(),
                new Payloads.RefundPayment(order.paymentId(), order.total(), "RESTAURANT_REJECTED"));
    }

    @Override
    public void assignDelivery(Order order, UUID causationId) {
        outbox.enqueue(MessageTypes.COMMANDS, MessageTypes.ASSIGN_DELIVERY, PRODUCER, order.id(),
                order.correlationId(), causationId, order.id(), order.version(),
                new Payloads.AssignDelivery(order.restaurantId(), new Payloads.Address(
                        order.deliveryAddress().line1(), order.deliveryAddress().city(),
                        order.deliveryAddress().instructions())));
    }

    private static List<Payloads.Item> items(Order order) {
        return order.items().stream()
                .map(i -> new Payloads.Item(i.menuItemId(), i.name(), i.quantity(), i.unitPrice()))
                .toList();
    }
}
