package com.eatsnow.notifications.infrastructure.adapter.out.identity;

import com.eatsnow.notifications.domain.exception.ContactNotFoundException;
import com.eatsnow.notifications.domain.exception.TransientNotificationException;
import com.eatsnow.notifications.domain.model.CustomerContact;
import com.eatsnow.notifications.domain.port.out.ContactLookupPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Calls the internal Identity endpoint (Spec 3.7) with a 2-second timeout. A timeout or a server error is transient and
 * retried by the dispatcher; a 404 is permanent. The result is never cached and never logged.
 *
 * DEVIATION (Wave 2): the spec asks for an OAuth 2.0 client-credentials service token issued by Identity. Identity does
 * not issue those yet, so a shared service key travels in X-Service-Key. TODO: replace with client credentials.
 */
@Component
public class RestContactLookupAdapter implements ContactLookupPort {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final String identityUrl;
    private final String serviceKey;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public RestContactLookupAdapter(@Value("${eatsnow.identity.url}") String identityUrl,
                                    @Value("${eatsnow.identity.service-key}") String serviceKey, ObjectMapper mapper) {
        this.identityUrl = identityUrl;
        this.serviceKey = serviceKey;
        this.mapper = mapper;
    }

    @Override
    public CustomerContact find(UUID profileId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(identityUrl + "/internal/v1/profiles/" + profileId + "/contact"))
                .timeout(TIMEOUT).header("X-Service-Key", serviceKey).GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
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
