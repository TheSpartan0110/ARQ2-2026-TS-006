package com.eatsnow.notifications.application.service;

import com.eatsnow.notifications.domain.model.NotificationTrigger;
import java.util.UUID;

/** Plain-text email content per trigger. The delivery address and payment data are never included. */
final class EmailTemplates {
    private EmailTemplates() { }

    static String subject(NotificationTrigger trigger) {
        return switch (trigger) {
            case ACCEPTED -> "Your EatsNow order was accepted";
            case ON_THE_WAY -> "Your EatsNow order is on the way";
            case DELIVERED -> "Your EatsNow order was delivered";
            case REJECTED -> "Your EatsNow order could not be prepared";
            case PAYMENT_FAILED -> "We could not process the payment of your EatsNow order";
            case REFUNDED -> "Your EatsNow refund was confirmed";
        };
    }

    static String body(NotificationTrigger trigger, String displayName, UUID orderId) {
        String message = switch (trigger) {
            case ACCEPTED -> "The restaurant accepted your order and will start preparing it.";
            case ON_THE_WAY -> "A driver picked up your order and is on the way.";
            case DELIVERED -> "Your order was delivered. Enjoy your meal!";
            case REJECTED -> "Unfortunately the restaurant could not prepare your order. Your payment will be refunded.";
            case PAYMENT_FAILED -> "The payment was refused, so the order was not placed. You can try again with another payment method.";
            case REFUNDED -> "The refund of your order was confirmed.";
        };
        return "Hi " + displayName + ",\n\n" + message + "\n\nOrder reference: " + orderId + "\n\nThe EatsNow team\n";
    }
}
