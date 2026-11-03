package com.eatsnow.payments.domain.model;

/** Answer of the payment provider. UNKNOWN means "no answer": it is NOT a failure and NOT a success. */
public record ProviderResult(Outcome outcome, String providerReference, String failureCode) {

    public enum Outcome { AUTHORIZED, REFUNDED, FAILED, UNKNOWN }

    public static ProviderResult authorized(String reference) {
        return new ProviderResult(Outcome.AUTHORIZED, reference, null);
    }

    public static ProviderResult refunded(String reference) {
        return new ProviderResult(Outcome.REFUNDED, reference, null);
    }

    /** @param failureCode DECLINED, INSUFFICIENT_FUNDS, INVALID_PAYMENT_METHOD (charge) or REFUND_REFUSED (refund) */
    public static ProviderResult failed(String failureCode) {
        return new ProviderResult(Outcome.FAILED, null, failureCode);
    }

    public static ProviderResult unknown() {
        return new ProviderResult(Outcome.UNKNOWN, null, null);
    }

    public boolean isDefinitive() {
        return outcome != Outcome.UNKNOWN;
    }
}
