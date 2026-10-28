package com.eatsnow.catalog.domain.port.out;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.catalog.domain.model.OrderItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port for asymmetric cryptographic signing of quote tokens (Spec 2.3).
 */
public interface QuoteSignerPort {

    String signQuoteToken(
            UUID quoteId,
            UUID customerId,
            UUID restaurantId,
            List<OrderItem> items,
            Money total,
            Instant expiresAt
    );

    String getPublicKeyJwksJson();
}
