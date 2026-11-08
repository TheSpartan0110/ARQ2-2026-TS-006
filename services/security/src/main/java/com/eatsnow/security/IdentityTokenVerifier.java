package com.eatsnow.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifies Identity access tokens locally (Spec 3.1): pinned RS256 signature with cached keys, issuer, audience,
 * expiry and subject. Service tokens (audience eatsnow-internal) are rejected here.
 */
public class IdentityTokenVerifier {
    public static final String API_AUDIENCE = "eatsnow-api";

    private final JwksKeySource identityKeys;
    private final String issuer;

    public IdentityTokenVerifier(JwksKeySource identityKeys, String issuer) {
        this.identityKeys = identityKeys;
        this.issuer = issuer;
    }

    public Optional<AuthenticatedUser> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!identityKeys.verifySignature(jwt)) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!issuer.equals(claims.getIssuer()) || claims.getAudience() == null
                    || !claims.getAudience().contains(API_AUDIENCE) || claims.getExpirationTime() == null
                    || claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }
            List<String> roles = claims.getStringListClaim("roles");
            String restaurantId = claims.getStringClaim("restaurantId");
            String driverId = claims.getStringClaim("driverId");
            return Optional.of(new AuthenticatedUser(UUID.fromString(claims.getSubject()),
                    new HashSet<>(roles != null ? roles : List.of()),
                    restaurantId != null ? UUID.fromString(restaurantId) : null,
                    driverId != null ? UUID.fromString(driverId) : null));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
