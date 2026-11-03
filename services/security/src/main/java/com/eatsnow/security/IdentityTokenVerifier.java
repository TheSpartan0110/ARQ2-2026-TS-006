package com.eatsnow.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifies Identity access tokens locally: pinned RS256 signature with cached keys, issuer, expiry and subject.
 * TODO: Identity does not issue an `aud` claim yet; add the audience check (Spec 3.1) once it does.
 */
public class IdentityTokenVerifier {
    private static final String ISSUER = "eatsnow-identity";

    private final JwksKeySource identityKeys;

    public IdentityTokenVerifier(JwksKeySource identityKeys) {
        this.identityKeys = identityKeys;
    }

    public Optional<AuthenticatedUser> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!identityKeys.verifySignature(jwt)) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(claims.getIssuer()) || claims.getExpirationTime() == null
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
