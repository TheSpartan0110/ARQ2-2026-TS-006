package com.eatsnow.orders.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.orders.domain.exception.InconsistentEventException;
import com.eatsnow.orders.domain.model.DeliveryAddress;
import com.eatsnow.orders.domain.model.Order;
import com.eatsnow.orders.domain.model.OrderEvent;
import com.eatsnow.orders.domain.model.OrderItem;
import com.eatsnow.orders.domain.model.OrderStatus;
import com.eatsnow.orders.domain.model.PaymentStatus;
import com.eatsnow.orders.domain.model.PendingEvent;
import com.eatsnow.orders.domain.port.out.OrderEventPublisherPort;
import com.eatsnow.orders.domain.port.out.OrderRepositoryPort;
import com.eatsnow.orders.domain.port.out.OrderSecretPort;
import com.eatsnow.orders.domain.port.out.PendingEventStorePort;
import com.eatsnow.orders.domain.port.out.SentCommandPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderWorkflowServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private static final UUID RESTAURANT = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID PAYMENT = UUID.fromString("00000000-0000-4000-8000-000000000080");
    private static final UUID DELIVERY = UUID.fromString("00000000-0000-4000-8000-000000000070");
    private static final UUID DRIVER = UUID.fromString("00000000-0000-4000-8000-000000000020");
    private static final UUID MSG = UUID.fromString("00000000-0000-4000-8000-000000000099");
    private static final Money TOTAL = Money.cop(2500000);

    @Mock OrderRepositoryPort orders;
    @Mock OrderSecretPort secrets;
    @Mock SentCommandPort sentCommands;
    @Mock OrderEventPublisherPort events;
    @Mock OrderTransitions transitions;
    @Mock PendingEventStorePort pendingEvents;
    @InjectMocks OrderWorkflowService service;

    private Order order(OrderStatus status, PaymentStatus payment) {
        return order(status, payment, null, null);
    }

    private Order order(OrderStatus status, PaymentStatus payment, UUID deliveryId, UUID driverId) {
        return new Order(ORDER_ID, UUID.randomUUID(), RESTAURANT, UUID.randomUUID(),
                List.of(new OrderItem(UUID.randomUUID(), "Classic Burger", 1, TOTAL)), TOTAL,
                new DeliveryAddress("123 Example Street", "Medellin", null), status, payment, UUID.randomUUID(),
                PAYMENT, deliveryId, driverId, 1, Instant.now(), Instant.now());
    }

    private void given(Order order) {
        when(orders.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(transitions.apply(any(), any(), any(), any())).thenReturn(order);
    }

    // ---------------------------------------------------------------- payment events

    @Test
    void orderCreatedRequestsTheChargeExactlyOnce() {
        Order order = order(OrderStatus.CREATED, PaymentStatus.PENDING);
        given(order);
        when(sentCommands.markSent(ORDER_ID, "ChargePayment")).thenReturn(true, false);
        when(secrets.find(ORDER_ID)).thenReturn(Optional.of("encrypted"));

        service.onOrderCreated(ORDER_ID, MSG);
        service.onOrderCreated(ORDER_ID, UUID.randomUUID());   // semantic duplicate with a new messageId

        verify(events).chargePayment(order, "encrypted", MSG);
    }

    @Test
    void paymentAuthorizedKeepsCreatedStoresThePaymentAndRequestsTheRestaurantOnce() {
        Order order = order(OrderStatus.CREATED, PaymentStatus.PENDING);
        given(order);
        when(sentCommands.markSent(ORDER_ID, "RequestRestaurantAcceptance")).thenReturn(true);

        service.onPaymentAuthorized(ORDER_ID, PAYMENT, TOTAL, MSG);

        verify(transitions).apply(argThat((Order o) -> PAYMENT.equals(o.paymentId())), eq(OrderStatus.CREATED),
                eq(PaymentStatus.AUTHORIZED), eq(MSG));
        verify(secrets).delete(ORDER_ID);
        verify(events).requestRestaurantAcceptance(any(), eq(MSG));
    }

    @Test
    void duplicateOrStalePaymentAuthorizedHasNoEffect() {
        given(order(OrderStatus.CREATED, PaymentStatus.AUTHORIZED));

        service.onPaymentAuthorized(ORDER_ID, PAYMENT, TOTAL, MSG);

        verifyNoInteractions(events);
        verify(transitions, never()).apply(any(), any(), any(), any());
    }

    @Test
    void paymentAuthorizedWithDifferentAmountIsQuarantined() {
        given(order(OrderStatus.CREATED, PaymentStatus.PENDING));

        assertThrows(InconsistentEventException.class,
                () -> service.onPaymentAuthorized(ORDER_ID, PAYMENT, Money.cop(1), MSG));
    }

    @Test
    void eventForUnknownOrderIsQuarantined() {
        when(orders.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());

        assertThrows(InconsistentEventException.class, () -> service.onPaymentFailed(ORDER_ID, MSG));
    }

    @Test
    void definitiveFailureMovesTheOrderToPaymentFailed() {
        Order order = order(OrderStatus.CREATED, PaymentStatus.PENDING);
        given(order);

        service.onPaymentFailed(ORDER_ID, MSG);

        verify(transitions).apply(order, OrderStatus.PAYMENT_FAILED, PaymentStatus.FAILED, MSG);
    }

    @Test
    void timeoutOfTheChargeKeepsTheOrderCreatedWithUnknownPayment() {
        Order order = order(OrderStatus.CREATED, PaymentStatus.PENDING);
        given(order);

        service.onPaymentPendingReconciliation(ORDER_ID, "CHARGE", MSG);

        verify(transitions).apply(order, OrderStatus.CREATED, PaymentStatus.UNKNOWN, MSG);
    }

    @Test
    void aRefundTimeoutNeverRegressesTheOriginalCharge() {
        given(order(OrderStatus.REJECTED, PaymentStatus.REFUND_PENDING));

        service.onPaymentPendingReconciliation(ORDER_ID, "REFUND", MSG);

        verify(transitions, never()).apply(any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- restaurant events

    @Test
    void acceptanceOfAPaidOrderMovesItToAccepted() {
        Order order = order(OrderStatus.CREATED, PaymentStatus.AUTHORIZED);
        given(order);

        service.handle(new OrderEvent.RestaurantAccepted(ORDER_ID, MSG, RESTAURANT));

        verify(transitions).apply(order, OrderStatus.ACCEPTED, PaymentStatus.AUTHORIZED, MSG);
    }

    @Test
    void acceptanceBeforePaymentIsAuthorizedWaitsDurably() {
        given(order(OrderStatus.CREATED, PaymentStatus.UNKNOWN));
        OrderEvent event = new OrderEvent.RestaurantAccepted(ORDER_ID, MSG, RESTAURANT);

        service.handle(event);

        verify(pendingEvents).store(event);
        verify(transitions, never()).apply(any(), any(), any(), any());
    }

    @Test
    void preparationBeforeAcceptanceWaitsInsteadOfSkippingAccepted() {
        given(order(OrderStatus.CREATED, PaymentStatus.AUTHORIZED));
        OrderEvent event = new OrderEvent.RestaurantPreparing(ORDER_ID, MSG, RESTAURANT);

        service.handle(event);

        verify(pendingEvents).store(event);
        verify(transitions, never()).apply(any(), any(), any(), any());
    }

    @Test
    void preparationOfAnAcceptedOrderRequestsTheDeliveryOnce() {
        Order order = order(OrderStatus.ACCEPTED, PaymentStatus.AUTHORIZED);
        given(order);
        when(sentCommands.markSent(ORDER_ID, "AssignDelivery")).thenReturn(true);

        service.handle(new OrderEvent.RestaurantPreparing(ORDER_ID, MSG, RESTAURANT));

        verify(transitions).apply(order, OrderStatus.PREPARING, PaymentStatus.AUTHORIZED, MSG);
        verify(events).assignDelivery(any(), eq(MSG));
    }

    @Test
    void rejectionOfAPaidOrderRequestsOneRefundAndKeepsRefundPending() {
        Order order = order(OrderStatus.CREATED, PaymentStatus.AUTHORIZED);
        given(order);
        when(sentCommands.markSent(ORDER_ID, "RefundPayment")).thenReturn(true);

        service.handle(new OrderEvent.RestaurantRejected(ORDER_ID, MSG, RESTAURANT, "Kitchen closed"));

        verify(transitions).apply(order, OrderStatus.REJECTED, PaymentStatus.REFUND_PENDING, MSG);
        verify(events).refundPayment(any(), eq(MSG));
    }

    @Test
    void rejectionAfterAcceptanceContradictsTheOrderAndIsQuarantined() {
        given(order(OrderStatus.ACCEPTED, PaymentStatus.AUTHORIZED));

        assertThrows(InconsistentEventException.class,
                () -> service.handle(new OrderEvent.RestaurantRejected(ORDER_ID, MSG, RESTAURANT, "late")));
    }

    @Test
    void aDecisionOfAnotherRestaurantIsQuarantined() {
        given(order(OrderStatus.CREATED, PaymentStatus.AUTHORIZED));

        assertThrows(InconsistentEventException.class,
                () -> service.handle(new OrderEvent.RestaurantAccepted(ORDER_ID, MSG, UUID.randomUUID())));
    }

    @Test
    void aDuplicateAcceptanceHasNoEffect() {
        given(order(OrderStatus.ACCEPTED, PaymentStatus.AUTHORIZED));

        service.handle(new OrderEvent.RestaurantAccepted(ORDER_ID, MSG, RESTAURANT));

        verify(transitions, never()).apply(any(), any(), any(), any());
        verifyNoInteractions(events);
    }

    // ---------------------------------------------------------------- delivery events

    @Test
    void assignmentKeepsPreparingAndStoresTheReference() {
        Order order = order(OrderStatus.PREPARING, PaymentStatus.AUTHORIZED);
        given(order);

        service.handle(new OrderEvent.DeliveryAssigned(ORDER_ID, MSG, DELIVERY, DRIVER));

        verify(transitions).recordAssignment(order, DELIVERY, DRIVER);
        verify(transitions, never()).apply(any(), any(), any(), any());
    }

    @Test
    void pickupBeforeAssignmentWaitsForIt() {
        given(order(OrderStatus.PREPARING, PaymentStatus.AUTHORIZED));
        OrderEvent event = new OrderEvent.DeliveryPickedUp(ORDER_ID, MSG, DELIVERY, DRIVER);

        service.handle(event);

        verify(pendingEvents).store(event);
    }

    @Test
    void pickupByTheAssignedDriverMovesTheOrderOnTheWay() {
        Order order = order(OrderStatus.PREPARING, PaymentStatus.AUTHORIZED, DELIVERY, DRIVER);
        given(order);

        service.handle(new OrderEvent.DeliveryPickedUp(ORDER_ID, MSG, DELIVERY, DRIVER));

        verify(transitions).apply(order, OrderStatus.ON_THE_WAY, PaymentStatus.AUTHORIZED, MSG);
    }

    @Test
    void pickupByAnotherDriverIsQuarantined() {
        given(order(OrderStatus.PREPARING, PaymentStatus.AUTHORIZED, DELIVERY, DRIVER));

        assertThrows(InconsistentEventException.class,
                () -> service.handle(new OrderEvent.DeliveryPickedUp(ORDER_ID, MSG, DELIVERY, UUID.randomUUID())));
    }

    @Test
    void completionOfAPickedUpDeliveryMovesTheOrderToDelivered() {
        Order order = order(OrderStatus.ON_THE_WAY, PaymentStatus.AUTHORIZED, DELIVERY, DRIVER);
        given(order);

        service.handle(new OrderEvent.DeliveryCompleted(ORDER_ID, MSG, DELIVERY, DRIVER));

        verify(transitions).apply(order, OrderStatus.DELIVERED, PaymentStatus.AUTHORIZED, MSG);
    }

    @Test
    void completionBeforePickupWaitsDurably() {
        given(order(OrderStatus.PREPARING, PaymentStatus.AUTHORIZED, DELIVERY, DRIVER));
        OrderEvent event = new OrderEvent.DeliveryCompleted(ORDER_ID, MSG, DELIVERY, DRIVER);

        service.handle(event);

        verify(pendingEvents).store(event);
    }

    // ---------------------------------------------------------------- refund

    @Test
    void refundConfirmationKeepsRejectedAndMarksThePaymentRefunded() {
        Order order = order(OrderStatus.REJECTED, PaymentStatus.REFUND_PENDING);
        given(order);

        service.handle(new OrderEvent.PaymentRefunded(ORDER_ID, MSG, PAYMENT, TOTAL));

        verify(transitions).apply(order, OrderStatus.REJECTED, PaymentStatus.REFUNDED, MSG);
    }

    @Test
    void refundOfADifferentAmountIsQuarantined() {
        given(order(OrderStatus.REJECTED, PaymentStatus.REFUND_PENDING));

        assertThrows(InconsistentEventException.class,
                () -> service.handle(new OrderEvent.PaymentRefunded(ORDER_ID, MSG, PAYMENT, Money.cop(1))));
    }

    @Test
    void refundOfAnOrderThatWasNotRejectedIsQuarantined() {
        given(order(OrderStatus.CREATED, PaymentStatus.AUTHORIZED));

        assertThrows(InconsistentEventException.class,
                () -> service.handle(new OrderEvent.PaymentRefunded(ORDER_ID, MSG, PAYMENT, TOTAL)));
    }

    // ---------------------------------------------------------------- re-evaluation of pending events

    @Test
    void aTransitionReevaluatesAndAppliesTheEventThatWasWaiting() {
        Order created = order(OrderStatus.CREATED, PaymentStatus.AUTHORIZED);
        Order accepted = order(OrderStatus.ACCEPTED, PaymentStatus.AUTHORIZED);
        UUID preparingMessage = UUID.randomUUID();
        PendingEvent waiting = new PendingEvent(7, new OrderEvent.RestaurantPreparing(ORDER_ID, preparingMessage, RESTAURANT));
        when(orders.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(created), Optional.of(accepted));
        when(transitions.apply(any(), any(), any(), any())).thenReturn(accepted);
        when(pendingEvents.findPending(ORDER_ID)).thenReturn(List.of(waiting), List.of());
        when(sentCommands.markSent(ORDER_ID, "AssignDelivery")).thenReturn(true);

        service.handle(new OrderEvent.RestaurantAccepted(ORDER_ID, MSG, RESTAURANT));

        verify(transitions).apply(created, OrderStatus.ACCEPTED, PaymentStatus.AUTHORIZED, MSG);
        verify(transitions).apply(accepted, OrderStatus.PREPARING, PaymentStatus.AUTHORIZED, preparingMessage);
        verify(pendingEvents).delete(7);
    }

    @Test
    void aPendingEventThatNowContradictsTheOrderIsQuarantinedNotApplied() {
        PendingEvent waiting = new PendingEvent(9, new OrderEvent.RestaurantRejected(ORDER_ID, MSG, RESTAURANT, "late"));
        when(orders.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.ACCEPTED, PaymentStatus.AUTHORIZED)));
        when(pendingEvents.findPending(ORDER_ID)).thenReturn(List.of(waiting), List.of());

        service.reevaluatePending(ORDER_ID);

        verify(pendingEvents).quarantine(9);
        verify(transitions, never()).apply(any(), any(), any(), any());
    }
}
