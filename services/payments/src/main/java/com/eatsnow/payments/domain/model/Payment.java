package com.eatsnow.payments.domain.model;

import com.eatsnow.common.domain.model.Money;
import java.util.UUID;

/**
 * One logical payment per order (Spec 2.2). The provider idempotency key never changes, so retries and timeouts can
 * never produce a second charge. The encrypted token is cleared once the outcome is definitive.
 */
public record Payment(UUID id, UUID orderId, Money total, String idempotencyKey, String encryptedPaymentToken,
                      UUID correlationId, UUID commandMessageId, String traceparent, PaymentStatus status,
                      int version) {
}
