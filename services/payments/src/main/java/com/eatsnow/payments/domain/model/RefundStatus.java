package com.eatsnow.payments.domain.model;

/** Lifecycle of a refund attempt. FAILED is a permanent provider refusal that needs manual reconciliation. */
public enum RefundStatus {
    PENDING, UNKNOWN, REFUNDED, FAILED
}
