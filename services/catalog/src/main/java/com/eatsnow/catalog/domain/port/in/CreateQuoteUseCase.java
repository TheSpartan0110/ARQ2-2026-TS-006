package com.eatsnow.catalog.domain.port.in;

import com.eatsnow.catalog.domain.model.Quote;
import com.eatsnow.catalog.domain.model.RequestedItem;

import java.util.List;
import java.util.UUID;

public interface CreateQuoteUseCase {
    Quote createQuote(UUID customerId, UUID restaurantId, List<RequestedItem> items, String idempotencyKey, String rawPayload);
}
