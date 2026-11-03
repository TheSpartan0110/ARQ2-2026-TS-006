package com.eatsnow.payments.domain.port.in;

import com.eatsnow.payments.domain.model.Payment;
import com.eatsnow.payments.domain.model.Refund;
import java.util.List;

/** Provider interaction and reconciliation (Spec 4.3 item 5). Driven by the scheduler adapter. */
public interface PaymentProcessingUseCase {
    /** Payments recorded as PENDING that still need a provider call. */
    List<Payment> pendingCharges(int limit);

    /** Calls the provider (idempotent key, no database transaction open) and records the outcome atomically. */
    void charge(Payment payment);

    /** Payments whose outcome is UNKNOWN and must be looked up at the provider. */
    List<Payment> unresolvedPayments(int limit);

    /** Looks the charge up by its idempotency key; a definitive answer is recorded, otherwise nothing changes. */
    void reconcile(Payment payment);

    /** Age in seconds of the oldest UNKNOWN payment, 0 if none (feeds the PaymentUnresolved alert). */
    long oldestUnresolvedSeconds();

    List<Refund> pendingRefunds(int limit);

    /** Calls the provider with the stable refund key (no transaction open) and records the outcome atomically. */
    void refund(Refund refund);

    List<Refund> unresolvedRefunds(int limit);

    /** Looks the refund up by its key; a definitive answer is recorded, otherwise nothing changes. */
    void reconcileRefund(Refund refund);

    /** Age in seconds of the oldest refund that is not confirmed, 0 if none (feeds PaymentUnresolved). */
    long oldestUnresolvedRefundSeconds();
}
