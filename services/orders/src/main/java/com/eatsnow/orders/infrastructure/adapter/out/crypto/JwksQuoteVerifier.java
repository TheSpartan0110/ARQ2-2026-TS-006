package com.eatsnow.orders.infrastructure.adapter.out.crypto;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.orders.domain.exception.InvalidQuoteException;
import com.eatsnow.orders.domain.exception.QuoteExpiredException;
import com.eatsnow.orders.domain.model.OrderItem;
import com.eatsnow.orders.domain.model.Quote;
import com.eatsnow.orders.domain.port.out.QuoteVerifierPort;
import com.eatsnow.security.JwksKeySource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Verifies the signed quote issued by Catalog (Spec 2.3): pinned RS256 signature with locally cached public keys,
 * issuer "catalog", audience "orders", expiry, customer binding, item limits and totals.
 */
@Component
public class JwksQuoteVerifier implements QuoteVerifierPort {
    private static final String ISSUER = "catalog";
    private static final String AUDIENCE = "orders";
    private static final ObjectMapper LENIENT = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final JwksKeySource catalogKeys;

    public JwksQuoteVerifier(@Qualifier("catalogJwks") JwksKeySource catalogKeys) {
        this.catalogKeys = catalogKeys;
    }

    @Override
    public Quote verify(String quoteToken, UUID customerId) {
        SignedJWT jwt;
        JWTClaimsSet claims;
        try {
            jwt = SignedJWT.parse(quoteToken);
            claims = jwt.getJWTClaimsSet();
        } catch (Exception e) {
            throw new InvalidQuoteException("The quote token is not valid");
        }
        if (!catalogKeys.verifySignature(jwt)) {
            throw new InvalidQuoteException("The quote signature cannot be verified");
        }
        if (!ISSUER.equals(claims.getIssuer()) || claims.getAudience() == null
                || !claims.getAudience().contains(AUDIENCE)) {
            throw new InvalidQuoteException("The quote was not issued for Orders");
        }
        if (claims.getExpirationTime() == null) {
            throw new InvalidQuoteException("The quote has no expiry");
        }
        Instant expiresAt = claims.getExpirationTime().toInstant();
        if (expiresAt.isBefore(Instant.now())) {
            throw new QuoteExpiredException();
        }

        try {
            UUID quoteId = UUID.fromString(claims.getJWTID());
            UUID tokenCustomer = UUID.fromString(claims.getStringClaim("customerId"));
            UUID restaurantId = UUID.fromString(claims.getStringClaim("restaurantId"));
            if (!tokenCustomer.equals(customerId)) {
                throw new InvalidQuoteException("The quote belongs to another customer");
            }
            List<OrderItem> items = LENIENT.convertValue(claims.getClaim("items"), new TypeReference<List<OrderItem>>() { });
            Money total = LENIENT.convertValue(claims.getClaim("total"), Money.class);
            validateItemsAndTotal(items, total);
            return new Quote(quoteId, tokenCustomer, restaurantId, List.copyOf(items), total, expiresAt);
        } catch (InvalidQuoteException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidQuoteException("The quote content is not valid");
        }
    }

    private void validateItemsAndTotal(List<OrderItem> items, Money total) {
        if (items == null || items.isEmpty() || items.size() > 50 || total == null) {
            throw new InvalidQuoteException("The quote must contain between 1 and 50 items and a total");
        }
        Money sum = Money.cop(0);
        for (OrderItem item : items) {
            if (item.menuItemId() == null || item.quantity() < 1 || item.quantity() > 20 || item.unitPrice() == null
                    || !total.currency().equals(item.unitPrice().currency())) {
                throw new InvalidQuoteException("The quote contains an invalid item");
            }
            sum = sum.add(item.subtotal());
        }
        if (!sum.equals(total)) {
            throw new InvalidQuoteException("The quote total does not match its items");
        }
    }
}
