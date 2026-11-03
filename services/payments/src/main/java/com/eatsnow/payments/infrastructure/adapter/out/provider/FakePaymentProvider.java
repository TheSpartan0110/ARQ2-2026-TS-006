package com.eatsnow.payments.infrastructure.adapter.out.provider;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.payments.domain.model.ProviderResult;
import com.eatsnow.payments.domain.port.out.PaymentProviderPort;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Local test double of the External Payment Provider (Spec 1.1: Wave 2 uses a test double). The behaviour is chosen
 * by the synthetic payment method token, so every scenario of the spec can be reproduced:
 *
 *   tok_ok*              authorized
 *   tok_declined*        definitive refusal DECLINED
 *   tok_insufficient*    definitive refusal INSUFFICIENT_FUNDS
 *   tok_invalid*         definitive refusal INVALID_PAYMENT_METHOD
 *   tok_lost_response*   the charge SUCCEEDS at the provider but the response is lost (timeout); reconciliation finds
 *                        it with lookup(): "Reconcile a lost payment response"
 *   tok_timeout*         timeout and the provider never recorded the charge: stays UNKNOWN
 *                        (triggers the PaymentUnresolved alert after 5 minutes)
 *
 * The same token also decides how a later REFUND of that charge behaves:
 *   tok_refund_lost*     the refund SUCCEEDS but the response is lost (timeout); reconciliation finds it
 *   tok_refund_timeout*  refund timeout, never recorded: stays REFUND_PENDING (PaymentUnresolved alert)
 *   tok_refund_refused*  permanent refusal REFUND_REFUSED: operational alert, never reported as refunded
 *   anything else        refund confirmed
 *
 * Idempotent by key; state is kept in memory (lost on restart). Never use real card data.
 */
@Component
public class FakePaymentProvider implements PaymentProviderPort {
    private final Map<String, ProviderResult> charges = new ConcurrentHashMap<>();
    private final Map<String, String> tokens = new ConcurrentHashMap<>();       // charge key -> token
    private final Map<String, ProviderResult> refunds = new ConcurrentHashMap<>();

    @Override
    public ProviderResult charge(String idempotencyKey, Money amount, String paymentMethodToken)
            throws ProviderTimeoutException {
        ProviderResult existing = charges.get(idempotencyKey);
        if (existing != null) {
            return existing;                            // same key: same outcome, never a second charge
        }
        if (paymentMethodToken.startsWith("tok_timeout")) {
            throw new ProviderTimeoutException("provider did not answer");
        }
        ProviderResult result;
        if (paymentMethodToken.startsWith("tok_declined")) {
            result = ProviderResult.failed("DECLINED");
        } else if (paymentMethodToken.startsWith("tok_insufficient")) {
            result = ProviderResult.failed("INSUFFICIENT_FUNDS");
        } else if (paymentMethodToken.startsWith("tok_invalid")) {
            result = ProviderResult.failed("INVALID_PAYMENT_METHOD");
        } else {
            result = ProviderResult.authorized("fake-" + UUID.randomUUID());
        }
        charges.put(idempotencyKey, result);
        tokens.put(idempotencyKey, paymentMethodToken);
        if (paymentMethodToken.startsWith("tok_lost_response")) {
            throw new ProviderTimeoutException("response lost after the charge was recorded");
        }
        return result;
    }

    @Override
    public ProviderResult refund(String refundKey, String chargeKey, Money amount) throws ProviderTimeoutException {
        ProviderResult existing = refunds.get(refundKey);
        if (existing != null) {
            return existing;                            // same key: same outcome, never a second refund
        }
        String token = tokens.getOrDefault(chargeKey, "");
        if (token.startsWith("tok_refund_timeout")) {
            throw new ProviderTimeoutException("provider did not answer the refund");
        }
        ProviderResult result = token.startsWith("tok_refund_refused")
                ? ProviderResult.failed("REFUND_REFUSED")
                : ProviderResult.refunded("fake-refund-" + UUID.randomUUID());
        refunds.put(refundKey, result);
        if (token.startsWith("tok_refund_lost")) {
            throw new ProviderTimeoutException("refund response lost after it was recorded");
        }
        return result;
    }

    @Override
    public Optional<ProviderResult> lookupRefund(String refundKey) {
        return Optional.ofNullable(refunds.get(refundKey));
    }

    @Override
    public Optional<ProviderResult> lookup(String idempotencyKey) {
        return Optional.ofNullable(charges.get(idempotencyKey));
    }
}
