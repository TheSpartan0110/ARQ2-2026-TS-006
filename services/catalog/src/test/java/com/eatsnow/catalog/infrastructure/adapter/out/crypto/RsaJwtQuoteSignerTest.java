package com.eatsnow.catalog.infrastructure.adapter.out.crypto;

import com.eatsnow.catalog.domain.model.OrderItem;
import com.eatsnow.common.domain.model.Money;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RsaJwtQuoteSignerTest {

    private RsaJwtQuoteSigner quoteSigner;

    @BeforeEach
    void setUp() {
        quoteSigner = new RsaJwtQuoteSigner();
        quoteSigner.init();
    }

    @Test
    @DisplayName("Should sign JWT with RS256 containing all mandatory quote claims (Spec 2.3)")
    void shouldSignAndVerifyQuoteToken() throws Exception {
        UUID quoteId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID menuItemId = UUID.randomUUID();

        OrderItem item = new OrderItem(menuItemId, "Classic Burger", 2, Money.cop(2500000));
        Money total = Money.cop(5000000);
        Instant expiresAt = Instant.now().plusSeconds(120);

        String token = quoteSigner.signQuoteToken(
                quoteId,
                customerId,
                restaurantId,
                List.of(item),
                total,
                expiresAt
        );

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3); // Valid 3-part compact JWT

        // Parse token
        SignedJWT signedJWT = SignedJWT.parse(token);

        // Verify Header
        assertEquals("RS256", signedJWT.getHeader().getAlgorithm().getName());
        assertEquals("eatsnow-catalog-key-1", signedJWT.getHeader().getKeyID());

        // Verify Claims (Spec 2.3: iss, aud, exp, jti, customerId, restaurantId, items, total)
        assertEquals("catalog", signedJWT.getJWTClaimsSet().getIssuer());
        assertTrue(signedJWT.getJWTClaimsSet().getAudience().contains("orders"));
        assertEquals(quoteId.toString(), signedJWT.getJWTClaimsSet().getJWTID());
        assertEquals(customerId.toString(), signedJWT.getJWTClaimsSet().getStringClaim("customerId"));
        assertEquals(restaurantId.toString(), signedJWT.getJWTClaimsSet().getStringClaim("restaurantId"));
        assertNotNull(signedJWT.getJWTClaimsSet().getClaim("items"));
        assertNotNull(signedJWT.getJWTClaimsSet().getClaim("total"));

        // Cryptographically verify signature using the exported JWKS public key (as Orders will do)
        String jwksJson = quoteSigner.getPublicKeyJwksJson();
        assertNotNull(jwksJson);

        JWKSet jwkSet = JWKSet.parse(jwksJson);
        JWK jwk = jwkSet.getKeyByKeyId("eatsnow-catalog-key-1");
        assertNotNull(jwk);
        assertTrue(jwk instanceof RSAKey);

        RSAKey rsaKey = (RSAKey) jwk;
        JWSVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());

        assertTrue(signedJWT.verify(verifier), "JWT signature must verify using the public key from JWKS");
    }
}
