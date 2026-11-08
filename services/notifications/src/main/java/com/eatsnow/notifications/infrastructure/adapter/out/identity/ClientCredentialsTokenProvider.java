package com.eatsnow.notifications.infrastructure.adapter.out.identity;

import com.eatsnow.notifications.domain.exception.TransientNotificationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Service access token for internal calls (Spec 3.7, ADR 005): OAuth 2.0 client credentials issued by Identity. The
 * token is reused until shortly before it expires. It is never logged.
 */
class ClientCredentialsTokenProvider {
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final URI tokenEndpoint;
    private final String basicAuthorization;
    private final String scope;
    private final Duration timeout;

    private String token;
    private Instant renewAt = Instant.EPOCH;

    ClientCredentialsTokenProvider(HttpClient http, ObjectMapper mapper, String identityUrl, String clientId,
                                   String clientSecret, String scope, Duration timeout) {
        this.http = http;
        this.mapper = mapper;
        this.tokenEndpoint = URI.create(identityUrl + "/oauth2/token");
        this.basicAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        this.scope = scope;
        this.timeout = timeout;
    }

    synchronized String token() {
        if (token == null || Instant.now().isAfter(renewAt)) {
            fetch();
        }
        return token;
    }

    /** Drops the cached token, for example after Identity rejected it because its signing key changed. */
    synchronized void invalidate() {
        token = null;
    }

    private void fetch() {
        String form = "grant_type=client_credentials&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(tokenEndpoint).timeout(timeout)
                .header("Authorization", basicAuthorization)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new TransientNotificationException("Identity token endpoint answered HTTP " + response.statusCode(), null);
            }
            JsonNode body = mapper.readTree(response.body());
            token = body.path("access_token").asText();
            long expiresIn = body.path("expires_in").asLong(60);
            renewAt = Instant.now().plusSeconds(expiresIn).minus(EXPIRY_MARGIN);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientNotificationException("Interrupted while obtaining a service token", e);
        } catch (IOException e) {
            throw new TransientNotificationException("Identity token endpoint is unreachable", e);
        }
    }
}
