package com.eatsnow.gateway.security;

import com.eatsnow.gateway.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Rejects unauthenticated traffic before any business processing (Spec 3.1, Feature 1: 401 at the Gateway).
 * Verifies the Identity access token: pinned RS256 signature with locally cached keys, issuer and expiry.
 * Role and ownership checks stay in the services; they verify the token again (Spec 3.1).
 * TODO: Identity issues no `aud` claim yet; add the audience check once it does. TODO: 429 rate limiting.
 */
@Component
public class JwtValidationFilter implements GlobalFilter, Ordered {
    private static final String ISSUER = "eatsnow-identity";
    /** Login and Identity public keys are the only unauthenticated routes. */
    private static final Set<String> PUBLIC_PATHS = Set.of("/api/v1/auth/token", "/.well-known/jwks.json", "/oauth2/jwks");

    private final JwksKeySource identityKeys;
    private final ObjectMapper mapper;

    public JwtValidationFilter(@Value("${eatsnow.jwks.identity-url}") String jwksUrl, ObjectMapper mapper,
                               ObservationRegistry observations, MeterRegistry meters) {
        this.identityKeys = new JwksKeySource(jwksUrl, observations, meters);
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (PUBLIC_PATHS.contains(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(exchange, "A valid bearer access token is required");
        }
        // Verification may refresh the JWKS over HTTP (blocking): never do that on an event-loop thread.
        return Mono.fromCallable(() -> isValid(header.substring(7).trim()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(valid -> valid ? chain.filter(exchange) : reject(exchange, "The access token is invalid or expired"));
    }

    private boolean isValid(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!identityKeys.verifySignature(jwt)) {
                return false;
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            UUID.fromString(claims.getSubject());
            return ISSUER.equals(claims.getIssuer()) && claims.getExpirationTime() != null
                    && claims.getExpirationTime().toInstant().isAfter(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER);
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(Map.of("code", "UNAUTHENTICATED", "message", message,
                    "correlationId", String.valueOf(correlationId)));
        } catch (JsonProcessingException e) {
            body = "{\"code\":\"UNAUTHENTICATED\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;   // right after the correlation filter
    }
}
