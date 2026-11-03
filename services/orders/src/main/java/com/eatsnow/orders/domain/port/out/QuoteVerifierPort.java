package com.eatsnow.orders.domain.port.out;

import com.eatsnow.orders.domain.model.Quote;
import java.util.UUID;

/** Verifies a Catalog quote locally, without calling Catalog on the request path (Spec 1.2, 2.3). */
public interface QuoteVerifierPort {
    /**
     * @throws com.eatsnow.orders.domain.exception.InvalidQuoteException    bad signature, issuer, audience or totals
     * @throws com.eatsnow.orders.domain.exception.QuoteExpiredException    the quote expired
     */
    Quote verify(String quoteToken, UUID customerId);
}
