package com.eatsnow.identity.infrastructure.adapter.out.crypto;

import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.IssuedToken;
import com.eatsnow.identity.domain.model.Profile;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RsaTokenIssuerTest {

    private RsaTokenIssuer tokenIssuer;

    @BeforeEach
    void setUp() {
        tokenIssuer = new RsaTokenIssuer();
        tokenIssuer.init();
    }

    @Test
    @DisplayName("Should issue valid RS256 token with 15-minute expiration and required claims")
    void testIssueToken_ValidClaims() throws Exception {
        UUID profileId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        Profile profile = new Profile(
                profileId,
                "Burger Chef",
                "chef@burger.test",
                Set.of(Role.RESTAURANT),
                restaurantId,
                null,
                "hash",
                true,
                Instant.now(),
                Instant.now()
        );

        IssuedToken issuedToken = tokenIssuer.issueToken(profile);

        assertNotNull(issuedToken);
        assertEquals("Bearer", issuedToken.tokenType());
        assertEquals(900, issuedToken.expiresIn());
        assertNotNull(issuedToken.token());

        JWTClaimsSet claims = tokenIssuer.validateAndParse(issuedToken.token());
        assertNotNull(claims);
        assertEquals(RsaTokenIssuer.ISSUER, claims.getIssuer());
        assertEquals(profileId.toString(), claims.getSubject());
        assertEquals(restaurantId.toString(), claims.getStringClaim("restaurantId"));
        assertNull(claims.getStringClaim("driverId"));
        assertEquals("chef@burger.test", claims.getStringClaim("email"));
        assertEquals("Burger Chef", claims.getStringClaim("displayName"));

        List<String> roles = claims.getStringListClaim("roles");
        assertNotNull(roles);
        assertTrue(roles.contains("RESTAURANT"));
    }

    @Test
    @DisplayName("Should export valid public JWKS JSON containing RSA key")
    void testGetPublicJwksJson() {
        String jwks = tokenIssuer.getPublicJwksJson();

        assertNotNull(jwks);
        assertTrue(jwks.contains("keys"));
        assertTrue(jwks.contains(RsaTokenIssuer.KEY_ID));
        assertTrue(jwks.contains("RS256"));
    }

    @Test
    @DisplayName("Should return null when validating an invalid or corrupted token")
    void testValidateAndParse_CorruptedToken() {
        assertNull(tokenIssuer.validateAndParse("invalid.jwt.token"));
    }
}
