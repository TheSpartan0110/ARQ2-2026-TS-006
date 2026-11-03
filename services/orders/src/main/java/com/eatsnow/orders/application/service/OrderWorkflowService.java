package com.eatsnow.orders.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.orders.domain.exception.InconsistentEventException;
import com.eatsnow.orders.domain.model.Order;
import com.eatsnow.orders.domain.model.OrderEvent;
import com.eatsnow.orders.domain.model.OrderStatus;
import com.eatsnow.orders.domain.model.PaymentStatus;
import com.eatsnow.orders.domain.model.PendingEvent;
import com.eatsnow.orders.domain.port.in.HandleOrderEventUseCase;
import com.eatsnow.orders.domain.port.out.OrderEventPublisherPort;
import com.eatsnow.orders.domain.port.out.OrderRepositoryPort;
import com.eatsnow.orders.domain.port.out.OrderSecretPort;
import com.eatsnow.orders.domain.port.out.PendingEventStorePort;
import com.eatsnow.orders.domain.port.out.SentCommandPort;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orders is the orchestrator (ADR 001): it consumes events and decides the next command or state change (Spec 2.4).
 * Every method runs inside the transaction opened by the consumer, together with the deduplication record, and
 * locks the order row so state writes of one order are serialized (Spec 4.3 item 3).
 *
 * Each event is evaluated against the current state and has exactly one of three outcomes:
 * APPLIED (state changed), IGNORED (duplicate or stale: no effect) or PENDING (valid but premature: stored durably and
 * re-evaluated after every transition and every 10 seconds). A message that contradicts the order is quarantined.
 */
@Service
public class OrderWorkflowService implements HandleOrderEventUseCase {
    private static final Logger log = LoggerFactory.getLogger(OrderWorkflowService.class);
    private static final String CHARGE_PAYMENT = "ChargePayment";
    private static final String REQUEST_RESTAURANT_ACCEPTANCE = "RequestRestaurantAcceptance";
    private static final String REFUND_PAYMENT = "RefundPayment";
    private static final String ASSIGN_DELIVERY = "AssignDelivery";

    private enum Outcome { APPLIED, IGNORED, PENDING }

    private final OrderRepositoryPort orders;
    private final OrderSecretPort secrets;
    private final SentCommandPort sentCommands;
    private final OrderEventPublisherPort events;
    private final OrderTransitions transitions;
    private final PendingEventStorePort pendingEvents;

    public OrderWorkflowService(OrderRepositoryPort orders, OrderSecretPort secrets, SentCommandPort sentCommands,
                                OrderEventPublisherPort events, OrderTransitions transitions,
                                PendingEventStorePort pendingEvents) {
        this.orders = orders;
        this.secrets = secrets;
        this.sentCommands = sentCommands;
        this.events = events;
        this.transitions = transitions;
        this.pendingEvents = pendingEvents;
    }

    // ------------------------------------------------------------------ payment side

    @Override
    @Transactional
    public void onOrderCreated(UUID orderId, UUID messageId) {
        Order order = load(orderId);
        if (!sentCommands.markSent(order.id(), CHARGE_PAYMENT)) {
            return;                                   // semantic duplicate that arrived with a new messageId
        }
        String encryptedToken = secrets.find(order.id())
                .orElseThrow(() -> new InconsistentEventException("Payment token missing for order " + orderId));
        events.chargePayment(order, encryptedToken, messageId);
    }

    @Override
    @Transactional
    public void onPaymentAuthorized(UUID orderId, UUID paymentId, Money authorizedTotal, UUID messageId) {
        Order order = loadForUpdate(orderId);
        if (!order.total().equals(authorizedTotal)) {
            throw new InconsistentEventException("Authorized amount does not match the order total");
        }
        if (order.status() != OrderStatus.CREATED || !order.paymentStatus().isChargeOutcomeOpen()) {
            ignore("PaymentAuthorized", order);       // stale or duplicate: never regress or re-apply
            return;
        }
        Order updated = transitions.apply(order.withPaymentId(paymentId), OrderStatus.CREATED,
                PaymentStatus.AUTHORIZED, messageId);
        secrets.delete(order.id());                   // Payments has an outcome: remove the token (Spec 3.4)
        if (sentCommands.markSent(order.id(), REQUEST_RESTAURANT_ACCEPTANCE)) {
            events.requestRestaurantAcceptance(updated, messageId);
        }
        reevaluate(orderId);                          // a restaurant decision may be waiting for this payment
    }

    @Override
    @Transactional
    public void onPaymentFailed(UUID orderId, UUID messageId) {
        Order order = loadForUpdate(orderId);
        if (order.status() != OrderStatus.CREATED || !order.paymentStatus().isChargeOutcomeOpen()) {
            ignore("PaymentFailed", order);
            return;
        }
        transitions.apply(order, OrderStatus.PAYMENT_FAILED, PaymentStatus.FAILED, messageId);
        secrets.delete(order.id());
    }

    @Override
    @Transactional
    public void onPaymentPendingReconciliation(UUID orderId, String operation, UUID messageId) {
        Order order = loadForUpdate(orderId);
        // Only an open CHARGE outcome becomes UNKNOWN. A REFUND timeout must not regress the original charge status
        // (Spec 4.3 item 6): the order stays REJECTED with REFUND_PENDING.
        if ("CHARGE".equals(operation) && order.status() == OrderStatus.CREATED
                && order.paymentStatus() == PaymentStatus.PENDING) {
            transitions.apply(order, OrderStatus.CREATED, PaymentStatus.UNKNOWN, messageId);
            return;
        }
        ignore("PaymentPendingReconciliation", order);
    }

    // ------------------------------------------------------------------ restaurant, delivery and refund events

    @Override
    @Transactional
    public void handle(OrderEvent event) {
        Outcome outcome = apply(event);
        switch (outcome) {
            case PENDING -> {
                pendingEvents.store(event);
                log.info("Premature event stored until its prerequisite is met",
                        kv("operation", "consume " + event.getClass().getSimpleName()), kv("outcome", "PENDING"));
            }
            case APPLIED -> reevaluate(event.orderId());
            case IGNORED -> { }
        }
    }

    @Override
    @Transactional
    public void reevaluatePending(UUID orderId) {
        reevaluate(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> ordersWithPendingEvents(int limit) {
        return pendingEvents.orderIdsWithPending(limit);
    }

    @Override
    @Transactional(readOnly = true)
    public long oldestPendingSeconds() {
        return pendingEvents.oldestPendingSeconds();
    }

    private void reevaluate(UUID orderId) {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (PendingEvent pending : pendingEvents.findPending(orderId)) {
                Outcome outcome;
                try {
                    outcome = apply(pending.event());
                } catch (InconsistentEventException e) {
                    pendingEvents.quarantine(pending.id());       // contradictory: kept for investigation, not applied
                    log.error("Pending event contradicts the order and was quarantined: {}", e.getMessage(),
                            kv("operation", "reevaluate pending"), kv("outcome", "FAILURE"));
                    continue;
                }
                if (outcome != Outcome.PENDING) {
                    pendingEvents.delete(pending.id());
                    progress |= outcome == Outcome.APPLIED;
                }
            }
        }
    }

    private Outcome apply(OrderEvent event) {
        Order order = loadForUpdate(event.orderId());
        if (event instanceof OrderEvent.RestaurantAccepted e) {
            return accepted(order, e);
        } else if (event instanceof OrderEvent.RestaurantRejected e) {
            return rejected(order, e);
        } else if (event instanceof OrderEvent.RestaurantPreparing e) {
            return preparing(order, e);
        } else if (event instanceof OrderEvent.DeliveryAssigned e) {
            return deliveryAssigned(order, e);
        } else if (event instanceof OrderEvent.DeliveryPickedUp e) {
            return pickedUp(order, e);
        } else if (event instanceof OrderEvent.DeliveryCompleted e) {
            return completed(order, e);
        } else if (event instanceof OrderEvent.PaymentRefunded e) {
            return refunded(order, e);
        }
        throw new InconsistentEventException("Unsupported event " + event.getClass().getSimpleName());
    }

    /** CREATED + RestaurantAccepted with payment AUTHORIZED -> ACCEPTED. */
    private Outcome accepted(Order order, OrderEvent.RestaurantAccepted e) {
        requireRestaurant(order, e.restaurantId());
        switch (order.status()) {
            case CREATED -> {
                if (order.paymentStatus() != PaymentStatus.AUTHORIZED) {
                    return Outcome.PENDING;           // requires payment AUTHORIZED (Spec 4.2.2)
                }
                transitions.apply(order, OrderStatus.ACCEPTED, order.paymentStatus(), e.messageId());
                return Outcome.APPLIED;
            }
            case ACCEPTED, PREPARING, ON_THE_WAY, DELIVERED -> {
                return Outcome.IGNORED;
            }
            default -> throw contradiction("RestaurantAccepted", order);
        }
    }

    /** CREATED + RestaurantRejected with payment AUTHORIZED -> REJECTED, paymentStatus REFUND_PENDING, one refund. */
    private Outcome rejected(Order order, OrderEvent.RestaurantRejected e) {
        requireRestaurant(order, e.restaurantId());
        switch (order.status()) {
            case CREATED -> {
                if (order.paymentStatus() != PaymentStatus.AUTHORIZED) {
                    return Outcome.PENDING;
                }
                Order updated = transitions.apply(order, OrderStatus.REJECTED, PaymentStatus.REFUND_PENDING,
                        e.messageId());
                if (sentCommands.markSent(order.id(), REFUND_PAYMENT)) {
                    events.refundPayment(updated, e.messageId());
                }
                return Outcome.APPLIED;
            }
            case REJECTED -> {
                return Outcome.IGNORED;
            }
            default -> throw contradiction("RestaurantRejected", order);   // already accepted or payment failed
        }
    }

    /** ACCEPTED + RestaurantPreparing -> PREPARING and one AssignDelivery. */
    private Outcome preparing(Order order, OrderEvent.RestaurantPreparing e) {
        requireRestaurant(order, e.restaurantId());
        switch (order.status()) {
            case ACCEPTED -> {
                Order updated = transitions.apply(order, OrderStatus.PREPARING, order.paymentStatus(), e.messageId());
                if (sentCommands.markSent(order.id(), ASSIGN_DELIVERY)) {
                    events.assignDelivery(updated, e.messageId());
                }
                return Outcome.APPLIED;
            }
            case CREATED -> {
                return Outcome.PENDING;               // waits for RestaurantAccepted, never skips ACCEPTED
            }
            case PREPARING, ON_THE_WAY, DELIVERED -> {
                return Outcome.IGNORED;
            }
            default -> throw contradiction("RestaurantPreparing", order);
        }
    }

    /** PREPARING + DeliveryAssigned: keep PREPARING and store the assignment reference. */
    private Outcome deliveryAssigned(Order order, OrderEvent.DeliveryAssigned e) {
        switch (order.status()) {
            case PREPARING -> {
                if (order.deliveryId() == null) {
                    transitions.recordAssignment(order, e.deliveryId(), e.driverId());
                    return Outcome.APPLIED;
                }
                return sameAssignment(order, e.deliveryId(), e.driverId(), "DeliveryAssigned");
            }
            case CREATED, ACCEPTED -> {
                return Outcome.PENDING;
            }
            case ON_THE_WAY, DELIVERED -> {
                return sameAssignment(order, e.deliveryId(), e.driverId(), "DeliveryAssigned");
            }
            default -> throw contradiction("DeliveryAssigned", order);
        }
    }

    /** PREPARING + DeliveryPickedUp for the assigned delivery and driver -> ON_THE_WAY. */
    private Outcome pickedUp(Order order, OrderEvent.DeliveryPickedUp e) {
        switch (order.status()) {
            case PREPARING -> {
                if (order.deliveryId() == null) {
                    return Outcome.PENDING;           // requires DeliveryAssigned for the same delivery and driver
                }
                requireAssignment(order, e.deliveryId(), e.driverId(), "DeliveryPickedUp");
                transitions.apply(order, OrderStatus.ON_THE_WAY, order.paymentStatus(), e.messageId());
                return Outcome.APPLIED;
            }
            case CREATED, ACCEPTED -> {
                return Outcome.PENDING;
            }
            case ON_THE_WAY, DELIVERED -> {
                return sameAssignment(order, e.deliveryId(), e.driverId(), "DeliveryPickedUp");
            }
            default -> throw contradiction("DeliveryPickedUp", order);
        }
    }

    /** ON_THE_WAY + DeliveryCompleted for the same delivery -> DELIVERED. */
    private Outcome completed(Order order, OrderEvent.DeliveryCompleted e) {
        switch (order.status()) {
            case ON_THE_WAY -> {
                requireAssignment(order, e.deliveryId(), e.driverId(), "DeliveryCompleted");
                transitions.apply(order, OrderStatus.DELIVERED, order.paymentStatus(), e.messageId());
                return Outcome.APPLIED;
            }
            case CREATED, ACCEPTED, PREPARING -> {
                return Outcome.PENDING;               // waits until the pickup has been applied
            }
            case DELIVERED -> {
                return Outcome.IGNORED;
            }
            default -> throw contradiction("DeliveryCompleted", order);
        }
    }

    /** REJECTED + PaymentRefunded for the authorized amount -> paymentStatus REFUNDED (status stays REJECTED). */
    private Outcome refunded(Order order, OrderEvent.PaymentRefunded e) {
        if (order.status() != OrderStatus.REJECTED) {
            throw contradiction("PaymentRefunded", order);
        }
        if (!Objects.equals(order.paymentId(), e.paymentId()) || !order.total().equals(e.amount())) {
            throw new InconsistentEventException("Refunded payment or amount does not match the order");
        }
        if (order.paymentStatus() == PaymentStatus.REFUNDED) {
            return Outcome.IGNORED;
        }
        transitions.apply(order, OrderStatus.REJECTED, PaymentStatus.REFUNDED, e.messageId());
        return Outcome.APPLIED;
    }

    // ------------------------------------------------------------------ guards

    private void requireRestaurant(Order order, UUID restaurantId) {
        if (!order.restaurantId().equals(restaurantId)) {
            throw new InconsistentEventException("Restaurant does not match the order");
        }
    }

    private void requireAssignment(Order order, UUID deliveryId, UUID driverId, String type) {
        if (!Objects.equals(order.deliveryId(), deliveryId) || !Objects.equals(order.driverId(), driverId)) {
            throw new InconsistentEventException(type + " does not match the assigned delivery or driver");
        }
    }

    private Outcome sameAssignment(Order order, UUID deliveryId, UUID driverId, String type) {
        requireAssignment(order, deliveryId, driverId, type);
        return Outcome.IGNORED;
    }

    private InconsistentEventException contradiction(String type, Order order) {
        return new InconsistentEventException(type + " contradicts an order in state " + order.status());
    }

    private void ignore(String messageType, Order order) {
        log.info("Event has no effect in the current state", kv("operation", "consume " + messageType),
                kv("outcome", "IGNORED"), kv("orderStatus", order.status()), kv("paymentStatus", order.paymentStatus()));
    }

    private Order load(UUID orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> new InconsistentEventException("Unknown order " + orderId));
    }

    private Order loadForUpdate(UUID orderId) {
        return orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new InconsistentEventException("Unknown order " + orderId));
    }
}
