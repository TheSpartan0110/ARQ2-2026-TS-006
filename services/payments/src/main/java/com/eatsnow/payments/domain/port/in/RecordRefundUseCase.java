package com.eatsnow.payments.domain.port.in;

import com.eatsnow.common.domain.model.Money;
import java.util.UUID;

/** RefundPayment command: records the refund attempt and marks the payment REFUND_PENDING. */
public interface RecordRefundUseCase {

    record Command(UUID orderId, UUID paymentId, Money amount, UUID correlationId, UUID commandMessageId,
                   String traceparent) { }

    /**
     * @return false when a refund already exists for the payment (duplicate command)
     * @throws com.eatsnow.payments.domain.exception.PaymentNotReadyException the charge has no definitive outcome yet
     * @throws com.eatsnow.payments.domain.exception.InconsistentCommandException payment, order or amount mismatch
     */
    boolean recordRefund(Command command);
}
