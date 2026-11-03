package com.eatsnow.payments.domain.port.out;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.payments.domain.model.ProviderResult;
import java.util.Optional;

/**
 * Port to the External Payment Provider. A real adapter must be idempotent by key and support status lookup by that
 * key: both are what make timeouts recoverable without a second charge.
 */
public interface PaymentProviderPort {

    /** Authorizes and captures in one idempotent operation. */
    ProviderResult charge(String idempotencyKey, Money amount, String paymentMethodToken)
            throws ProviderTimeoutException;

    /** Status of an earlier charge by its key; empty if the provider has no record of it. */
    Optional<ProviderResult> lookup(String idempotencyKey);

    /**
     * Refunds a captured charge in one idempotent operation keyed by refundKey. chargeKey identifies the original charge.
     */
    ProviderResult refund(String refundKey, String chargeKey, Money amount) throws ProviderTimeoutException;

    /** Status of an earlier refund by its key; empty if the provider has no record of it. */
    Optional<ProviderResult> lookupRefund(String refundKey);

    /** No answer in time: the charge may or may not have happened. */
    class ProviderTimeoutException extends Exception {
        public ProviderTimeoutException(String message) {
            super(message);
        }
    }
}
