package com.eatsnow.notifications.infrastructure.adapter.out.identity;

import com.eatsnow.notifications.domain.exception.ContactNotFoundException;
import com.eatsnow.notifications.domain.exception.TransientNotificationException;
import com.eatsnow.notifications.domain.model.CustomerContact;
import com.eatsnow.notifications.domain.port.out.ContactLookupPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.java11.instrument.binder.jdk.MicrometerHttpClient;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Calls the internal Identity endpoint (Spec 3.7) with a 2-second timeout, authenticated with a client-credentials
 * service token issued by Identity. A timeout or a server error is transient and retried by the dispatcher; a 404 is
 * permanent. The result is never cached and never logged.
 */
@Component
public class RestContactLookupAdapter implements ContactLookupPort {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final String SCOPE = "internal.profiles.read";

    private final String identityUrl;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final ClientCredentialsTokenProvider serviceToken;

    public RestContactLookupAdapter(@Value("${eatsnow.identity.url}") String identityUrl,
                                    @Value("${eatsnow.identity.client-id}") String clientId,
                                    @Value("${eatsnow.identity.client-secret}") String clientSecret,
                                    ObjectMapper mapper, ObservationRegistry observations, MeterRegistry meters) {
        this.identityUrl = identityUrl;
        this.mapper = mapper;
        // Instrumented client: the lookup is a client span that propagates traceparent to Identity (Spec 6.3).
        this.http = MicrometerHttpClient.instrumentationBuilder(
                        HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), meters)
                .observationRegistry(observations)
                .build();
        this.serviceToken = new ClientCredentialsTokenProvider(http, mapper, identityUrl, clientId, clientSecret,
                SCOPE, TIMEOUT);
    }

    @Override
    public CustomerContact find(UUID profileId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(identityUrl + "/internal/v1/profiles/" + profileId + "/contact"))
                .timeout(TIMEOUT).header("Authorization", "Bearer " + serviceToken.token())
                .header(MicrometerHttpClient.URI_PATTERN_HEADER, "/internal/v1/profiles/{profileId}/contact")
                .GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                serviceToken.invalidate();             // e.g. Identity restarted with a new signing key; retried
                throw new TransientNotificationException("Identity rejected the service token", null);
            }
            if (response.statusCode() == 404) {
                throw new ContactNotFoundException("Identity has no contact for the profile");
            }
            if (response.statusCode() != 200) {
                throw new TransientNotificationException("Identity answered HTTP " + response.statusCode(), null);
            }
            JsonNode body = mapper.readTree(response.body());
            return new CustomerContact(profileId, body.path("displayName").asText(""), body.path("email").asText());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientNotificationException("Interrupted while contacting Identity", e);
        } catch (IOException e) {
            throw new TransientNotificationException("Identity is unreachable or answered garbage", e);
        }
    }
}
