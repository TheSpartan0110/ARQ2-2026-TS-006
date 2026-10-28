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

    @Test
    @DisplayName("Should fail validation if token signature has been tampered with")
    void testValidateAndParse_TamperedSignature() {
        Profile profile = new Profile(
                UUID.randomUUID(), "Tamper Test", "tamper@test.com", Set.of(Role.USER),
                null, null, "h", true, Instant.now(), Instant.now()
        );
        IssuedToken issued = tokenIssuer.issueToken(profile);
        String validToken = issued.token();

        // Tamper with the last 5 characters of the signature
        String tampered = validToken.substring(0, validToken.length() - 5) + "ABCDE";
        assertNull(tokenIssuer.validateAndParse(tampered), "Tampered token must fail validation");
    }

    @Test
    @DisplayName("Should fail validation if token is signed with a different RSA key")
    void testValidateAndParse_DifferentKey() {
        RsaTokenIssuer otherIssuer = new RsaTokenIssuer();
        otherIssuer.init();

        Profile profile = new Profile(
                UUID.randomUUID(), "Other", "other@test.com", Set.of(Role.USER),
                null, null, "h", true, Instant.now(), Instant.now()
        );
        IssuedToken otherToken = otherIssuer.issueToken(profile);

        assertNull(tokenIssuer.validateAndParse(otherToken.token()), "Token signed by another key must fail validation");
    }

    @Test
    @DisplayName("Should populate driverId claim when profile has DRIVER role")
    void testIssueToken_DriverClaim() throws Exception {
        UUID profileId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Profile driverProfile = new Profile(
                profileId, "Driver Dan", "dan@driver.test", Set.of(Role.DRIVER),
                null, driverId, "h", true, Instant.now(), Instant.now()
        );

        IssuedToken issued = tokenIssuer.issueToken(driverProfile);
        JWTClaimsSet claims = tokenIssuer.validateAndParse(issued.token());

        assertNotNull(claims);
        assertEquals(driverId.toString(), claims.getStringClaim("driverId"));
        assertNull(claims.getStringClaim("restaurantId"));
    }
}
