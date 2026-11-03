package com.eatsnow.orders.domain.port.in;

import com.eatsnow.orders.domain.model.DeliveryAddress;
import com.eatsnow.orders.domain.model.OrderReceipt;
import java.util.UUID;

/** POST /orders (Spec 3.4). */
public interface CreateOrderUseCase {

    /**
     * @param rawPayload canonical request body, used to hash the request for idempotency
     */
    record Command(UUID customerId, UUID idempotencyKey, String requestPath, String rawPayload, String quoteToken,
                   DeliveryAddress deliveryAddress, String paymentMethodToken, UUID requestCorrelationId) { }

    OrderReceipt createOrder(Command command);
}
