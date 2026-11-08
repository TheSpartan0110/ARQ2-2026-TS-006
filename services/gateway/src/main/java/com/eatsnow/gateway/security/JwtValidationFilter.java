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
import java.util.List;
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
 * Verifies the Identity access token: pinned RS256 signature with locally cached keys, issuer, audience and expiry.
 * Service tokens (audience eatsnow-internal) are rejected: internal endpoints are never routed (Spec 3.7).
 * Role and ownership checks stay in the services; they verify the token again (Spec 3.1).
 * TODO: 429 rate limiting.
 */
@Component
public class JwtValidationFilter implements GlobalFilter, Ordered {
    private static final String API_AUDIENCE = "eatsnow-api";
    /** The OIDC provider and its login, registration and activation pages are the only unauthenticated routes. */
    private static final Set<String> PUBLIC_PATHS = Set.of("/userinfo", "/login", "/logout", "/register", "/activate");
    private static final List<String> PUBLIC_PREFIXES = List.of("/oauth2/", "/.well-known/", "/connect/", "/css/");

    private final JwksKeySource identityKeys;
    private final ObjectMapper mapper;
    private final String issuer;

    public JwtValidationFilter(@Value("${eatsnow.jwks.identity-url}") String jwksUrl,
                               @Value("${eatsnow.identity.issuer}") String issuer, ObjectMapper mapper,
                               ObservationRegistry observations, MeterRegistry meters) {
        this.identityKeys = new JwksKeySource(jwksUrl, observations, meters);
        this.issuer = issuer;
        this.mapper = mapper;
    }

    static boolean isPublic(String path) {
        return PUBLIC_PATHS.contains(path) || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (isPublic(exchange.getRequest().getPath().value())) {
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
            return issuer.equals(claims.getIssuer())
                    && claims.getAudience() != null && claims.getAudience().contains(API_AUDIENCE)
                    && claims.getExpirationTime() != null
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
