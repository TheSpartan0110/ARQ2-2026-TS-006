package com.eatsnow.payments.domain.model;

import com.eatsnow.common.domain.model.Money;
import java.util.UUID;

/**
 * One logical refund per payment (Spec 4.2.2). The provider idempotency key derives from the paymentId and never
 * changes, so retries and timeouts can never produce a second refund.
 */
public record Refund(UUID id, UUID paymentId, UUID orderId, Money amount, String idempotencyKey,
                     String chargeIdempotencyKey, UUID correlationId, UUID commandMessageId, String traceparent,
                     RefundStatus status, int version) {
}
