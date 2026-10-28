package com.eatsnow.identity.infrastructure.adapter.out.crypto;

import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.IssuedToken;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.out.TokenIssuerPort;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Signs and validates RS256 JWT tokens with 15-minute expiration (Spec 3.1 & ADR 005).
 */
@Component
public class RsaTokenIssuer implements TokenIssuerPort {

    private static final Logger log = LoggerFactory.getLogger(RsaTokenIssuer.class);

    public static final String ISSUER = "eatsnow-identity";
    public static final String KEY_ID = "eatsnow-identity-key-1";
    public static final long TOKEN_EXPIRY_SECONDS = 900; // 15 minutes per Spec 3.1 & ADR 005

    private RSAKey rsaJwk;
    private JWSSigner signer;
    private JWSVerifier verifier;

    @PostConstruct
    public void init() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair keyPair = gen.generateKeyPair();

            this.rsaJwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(KEY_ID)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();

            this.signer = new RSASSASigner(rsaJwk);
            this.verifier = new RSASSAVerifier((RSAPublicKey) keyPair.getPublic());
            log.info("Initialized RSA Identity Token Issuer with key ID: {}", KEY_ID);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize RSA key pair for token issuance", ex);
        }
    }

    @Override
    public IssuedToken issueToken(Profile profile) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plus(TOKEN_EXPIRY_SECONDS, ChronoUnit.SECONDS);

            List<String> roleNames = profile.roles().stream()
                    .map(Role::name)
                    .toList();

            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .subject(profile.id().toString())
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiresAt))
                    .claim("roles", roleNames)
                    .claim("email", profile.email())
                    .claim("displayName", profile.displayName());

            if (profile.restaurantId() != null) {
                claimsBuilder.claim("restaurantId", profile.restaurantId().toString());
            }

            if (profile.driverId() != null) {
                claimsBuilder.claim("driverId", profile.driverId().toString());
            }

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                    claimsBuilder.build()
            );

            signedJWT.sign(signer);

            return new IssuedToken(
                    signedJWT.serialize(),
                    "Bearer",
                    TOKEN_EXPIRY_SECONDS,
                    profile.id(),
                    profile.roles(),
                    profile.restaurantId(),
                    profile.driverId()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to issue JWT access token", ex);
        }
    }

    @Override
    public String getPublicJwksJson() {
        RSAKey publicJwk = rsaJwk.toPublicJWK();
        return new JWKSet(publicJwk).toJSONObject().toString();
    }

    public JWTClaimsSet validateAndParse(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(verifier)) {
                return null;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime() != null && new Date().after(claims.getExpirationTime())) {
                return null;
            }

            return claims;
        } catch (Exception ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return null;
        }
    }
}
