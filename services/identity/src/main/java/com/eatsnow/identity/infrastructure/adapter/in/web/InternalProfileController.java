package com.eatsnow.identity.infrastructure.adapter.in.web;

import com.eatsnow.common.domain.exception.ForbiddenException;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.in.GetProfileUseCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint for Notifications (Spec 3.7). It is NOT routed by the API Gateway. Any other caller receives 403.
 *
 * DEVIATION (Wave 2): the spec requires an OAuth 2.0 client-credentials service token issued by Identity. Identity does
 * not issue those yet, so the caller proves itself with a shared key in X-Service-Key.
 * TODO: replace with client-credentials tokens whose client is listed for this endpoint.
 */
@RestController
public class InternalProfileController {

    public record ContactResponse(UUID profileId, String displayName, String email) { }

    private final GetProfileUseCase getProfileUseCase;
    private final String serviceKey;

    public InternalProfileController(GetProfileUseCase getProfileUseCase,
                                     @Value("${eatsnow.internal.service-key:dev-internal-service-key}") String serviceKey) {
        this.getProfileUseCase = getProfileUseCase;
        this.serviceKey = serviceKey;
    }

    @GetMapping("/internal/v1/profiles/{profileId}/contact")
    public ResponseEntity<ContactResponse> contact(
            @PathVariable UUID profileId,
            @RequestHeader(value = "X-Service-Key", required = false) String providedKey) {
        if (providedKey == null || !MessageDigest.isEqual(providedKey.getBytes(StandardCharsets.UTF_8),
                serviceKey.getBytes(StandardCharsets.UTF_8))) {                       // constant-time comparison
            throw new ForbiddenException("Only an allowed internal service can call this endpoint");
        }
        Profile profile = getProfileUseCase.getProfileById(profileId);                // 404 NOT_FOUND when absent
        return ResponseEntity.ok(new ContactResponse(profile.id(), profile.displayName(), profile.email()));
    }
}
