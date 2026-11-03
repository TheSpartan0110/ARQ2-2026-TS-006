package com.eatsnow.payments.domain.model;

/** Payment lifecycle (Spec 2.1). Refund states are implemented in step 4.2. */
public enum PaymentStatus {
    PENDING, UNKNOWN, AUTHORIZED, FAILED, REFUND_PENDING, REFUNDED
}
