package com.eatsnow.notifications.domain.model;

import java.util.Optional;

/** Events that produce an email (Spec 4.2.4). */
public enum NotificationTrigger {
    ACCEPTED, ON_THE_WAY, DELIVERED, REJECTED, PAYMENT_FAILED, REFUNDED;

    /**
     * ACCEPTED, ON_THE_WAY, DELIVERED, REJECTED and PAYMENT_FAILED fire when `status` changes to that value; REFUNDED
     * fires when `paymentStatus` is REFUNDED. Any other change is acknowledged without sending.
     */
    public static Optional<NotificationTrigger> from(String previousStatus, String status, String paymentStatus) {
        if ("REFUNDED".equals(paymentStatus)) {
            return Optional.of(REFUNDED);
        }
        if (status == null || status.equals(previousStatus)) {
            return Optional.empty();                       // the order status did not change
        }
        return switch (status) {
            case "ACCEPTED" -> Optional.of(ACCEPTED);
            case "ON_THE_WAY" -> Optional.of(ON_THE_WAY);
            case "DELIVERED" -> Optional.of(DELIVERED);
            case "REJECTED" -> Optional.of(REJECTED);
            case "PAYMENT_FAILED" -> Optional.of(PAYMENT_FAILED);
            default -> Optional.empty();
        };
    }
}
