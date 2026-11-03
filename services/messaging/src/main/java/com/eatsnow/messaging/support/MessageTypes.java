package com.eatsnow.messaging.support;

/** Exchange names and message types from docs/spec.md section 4. */
public final class MessageTypes {
    private MessageTypes() { }

    public static final String EVENTS = "eatsnow.events.v1";
    public static final String COMMANDS = "eatsnow.commands.v1";
    public static final String RETRY = "eatsnow.retry.v1";
    public static final String DLX = "eatsnow.dlx.v1";

    // commands
    public static final String CHARGE_PAYMENT = "ChargePayment";
    public static final String REFUND_PAYMENT = "RefundPayment";
    public static final String REQUEST_RESTAURANT_ACCEPTANCE = "RequestRestaurantAcceptance";
    public static final String ASSIGN_DELIVERY = "AssignDelivery";

    // events
    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_STATUS_CHANGED = "OrderStatusChanged";
    public static final String PAYMENT_AUTHORIZED = "PaymentAuthorized";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String PAYMENT_PENDING_RECONCILIATION = "PaymentPendingReconciliation";
    public static final String PAYMENT_REFUNDED = "PaymentRefunded";
    public static final String RESTAURANT_ACCEPTED = "RestaurantAccepted";
    public static final String RESTAURANT_REJECTED = "RestaurantRejected";
    public static final String RESTAURANT_PREPARING = "RestaurantPreparing";
    public static final String DELIVERY_ASSIGNED = "DeliveryAssigned";
    public static final String DELIVERY_PICKED_UP = "DeliveryPickedUp";
    public static final String DELIVERY_COMPLETED = "DeliveryCompleted";
}
