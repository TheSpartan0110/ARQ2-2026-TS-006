package com.eatsnow.payments.domain.port.in;

import com.eatsnow.common.domain.model.Money;
import java.util.UUID;

/** ChargePayment command: only RECORDS the attempt. The provider is called later, outside any transaction. */
public interface RecordChargeUseCase {

    record Command(UUID orderId, Money total, String encryptedPaymentToken, UUID correlationId,
                   UUID commandMessageId, String traceparent) { }

    /** @return false when a payment already exists for the order (duplicate command), true when it was recorded */
    boolean recordCharge(Command command);
}
