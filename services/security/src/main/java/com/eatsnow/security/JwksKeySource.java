package com.eatsnow.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Locally cached public keys of a token issuer (Identity or Catalog), loaded from its JWKS endpoint.
 * Verification is never on the network path: the cache is used first. The algorithm is pinned to RS256 and unknown
 * keys fail closed (Spec 2.3). The issuers generate their key pair at startup, so after an issuer restart the cached
 * key no longer verifies: one rate-limited refresh is attempted, and if that does not help the token is rejected.
 */
public class JwksKeySource {
    private static final Logger log = LoggerFactory.getLogger(JwksKeySource.class);
    private static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(5);

    private final String url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private volatile JWKSet cache;
    private volatile Instant lastFetch = Instant.EPOCH;

    public JwksKeySource(String url) {
        this.url = url;
    }

    public boolean verifySignature(SignedJWT jwt) {
        if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
            return false;                                    // pinned algorithm: rejects "none", HS256, ...
        }
        String keyId = jwt.getHeader().getKeyID();
        if (verifyWith(cache, keyId, jwt)) {
            return true;
        }
        return refresh() && verifyWith(cache, keyId, jwt);
    }

    private boolean verifyWith(JWKSet set, String keyId, SignedJWT jwt) {
        if (set == null || keyId == null) {
            return false;
        }
        JWK jwk = set.getKeyByKeyId(keyId);
        if (!(jwk instanceof RSAKey rsaKey)) {
            return false;
        }
        try {
            return jwt.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()));
        } catch (JOSEException e) {
            return false;
        }
    }

    private synchronized boolean refresh() {
        if (Duration.between(lastFetch, Instant.now()).compareTo(MIN_REFRESH_INTERVAL) < 0) {
            return false;
        }
        lastFetch = Instant.now();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                cache = JWKSet.parse(response.body());
                return true;
            }
            log.warn("JWKS endpoint returned HTTP {}", response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Could not load JWKS: {}", e.getClass().getSimpleName());
        }
        return false;
    }
}
