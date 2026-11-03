package com.eatsnow.orders.domain.model;

/** Payment projection held by Orders (Spec 2.1). Payments remains the financial source of truth. */
public enum PaymentStatus {
    PENDING, UNKNOWN, AUTHORIZED, FAILED, REFUND_PENDING, REFUNDED;

    /** True while Payments has not reported a definitive outcome for the charge. */
    public boolean isChargeOutcomeOpen() {
        return this == PENDING || this == UNKNOWN;
    }
}
