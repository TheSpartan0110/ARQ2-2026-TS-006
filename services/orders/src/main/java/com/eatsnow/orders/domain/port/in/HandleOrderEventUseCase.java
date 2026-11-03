package com.eatsnow.orders.domain.port.in;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.orders.domain.model.OrderEvent;
import java.util.List;
import java.util.UUID;

/**
 * Event-driven transitions of the order state machine (Spec 2.4). Stale or duplicate events have no effect, early
 * events wait durably, and contradictory events are quarantined (Spec 4.3 item 3).
 */
public interface HandleOrderEventUseCase {
    /** CREATED + OrderCreated: request ChargePayment once. */
    void onOrderCreated(UUID orderId, UUID messageId);

    /** CREATED + PaymentAuthorized for the matching order and amount: request restaurant acceptance once. */
    void onPaymentAuthorized(UUID orderId, UUID paymentId, Money authorizedTotal, UUID messageId);

    /** CREATED + PaymentFailed (definitive failure only) -> PAYMENT_FAILED. */
    void onPaymentFailed(UUID orderId, UUID messageId);

    /** PaymentPendingReconciliation: CHARGE -> paymentStatus UNKNOWN; REFUND keeps REFUND_PENDING. */
    void onPaymentPendingReconciliation(UUID orderId, String operation, UUID messageId);

    /** Restaurant, delivery and refund events. */
    void handle(OrderEvent event);

    /** Re-evaluates the pending events of one order after a transition or on the 10-second timer. */
    void reevaluatePending(UUID orderId);

    List<UUID> ordersWithPendingEvents(int limit);

    long oldestPendingSeconds();
}
