package com.eatsnow.orders.infrastructure.adapter.out.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eatsnow.common.domain.model.Money;
import com.eatsnow.orders.domain.exception.InvalidQuoteException;
import com.eatsnow.orders.domain.exception.QuoteExpiredException;
import com.eatsnow.orders.domain.model.Quote;
import com.eatsnow.security.JwksKeySource;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JwksQuoteVerifierTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static RSAKey signingKey;
    private static RSAKey otherKey;

    @BeforeAll
    static void generateKeys() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID("eatsnow-catalog-key-1").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("eatsnow-catalog-key-1").generate();
    }

    /** A key source that trusts exactly one public key, without any HTTP call. */
    private static JwksKeySource trusting(RSAKey key) {
        return new JwksKeySource("http://unused.invalid/jwks.json") {
            @Override
            public boolean verifySignature(SignedJWT jwt) {
                try {
                    return jwt.verify(new RSASSAVerifier(key.toRSAPublicKey()));
                } catch (JOSEException e) {
                    return false;
                }
            }
        };
    }

    private static String token(RSAKey key, String audience, Date expiry, long totalMinor) throws JOSEException {
        Map<String, Object> item = Map.of("menuItemId", UUID.randomUUID().toString(), "name", "Classic Burger",
                "quantity", 1, "unitPrice", Map.of("amountMinor", 2500000L, "currency", "COP"));
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("catalog").audience(audience).jwtID(UUID.randomUUID().toString()).expirationTime(expiry)
                .claim("customerId", CUSTOMER.toString()).claim("restaurantId", UUID.randomUUID().toString())
                .claim("items", List.of(item))
                .claim("total", Map.of("amountMinor", totalMinor, "currency", "COP"))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static Date in(long seconds) {
        return new Date(System.currentTimeMillis() + seconds * 1000);
    }

    private final JwksQuoteVerifier verifier = new JwksQuoteVerifier(trusting(signingKey));

    @Test
    void acceptsAValidQuote() throws Exception {
        Quote quote = verifier.verify(token(signingKey, "orders", in(60), 2500000), CUSTOMER);

        assertEquals(Money.cop(2500000), quote.total());
        assertEquals(CUSTOMER, quote.customerId());
        assertEquals(1, quote.items().size());
    }

    @Test
    void rejectsAnExpiredQuote() throws Exception {
        assertThrows(QuoteExpiredException.class,
                () -> verifier.verify(token(signingKey, "orders", in(-5), 2500000), CUSTOMER));
    }

    @Test
    void rejectsAQuoteForAnotherCustomer() throws Exception {
        assertThrows(InvalidQuoteException.class,
                () -> verifier.verify(token(signingKey, "orders", in(60), 2500000), UUID.randomUUID()));
    }

    @Test
    void rejectsAQuoteForAnotherAudience() throws Exception {
        assertThrows(InvalidQuoteException.class,
                () -> verifier.verify(token(signingKey, "payments", in(60), 2500000), CUSTOMER));
    }

    @Test
    void rejectsATotalThatDoesNotMatchTheItems() throws Exception {
        assertThrows(InvalidQuoteException.class,
                () -> verifier.verify(token(signingKey, "orders", in(60), 1), CUSTOMER));
    }

    @Test
    void rejectsATokenSignedWithAnUntrustedKey() throws Exception {
        assertThrows(InvalidQuoteException.class,
                () -> verifier.verify(token(otherKey, "orders", in(60), 2500000), CUSTOMER));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(InvalidQuoteException.class, () -> verifier.verify("not-a-jwt", CUSTOMER));
    }
}
