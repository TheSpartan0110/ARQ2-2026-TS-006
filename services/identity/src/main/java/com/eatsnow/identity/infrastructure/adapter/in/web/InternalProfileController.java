package com.eatsnow.identity.infrastructure.adapter.in.web;

import com.eatsnow.common.domain.exception.ForbiddenException;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.in.GetProfileUseCase;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint for Notifications (Spec 3.7). It is NOT routed by the API Gateway. The security chain already
 * requires a client-credentials token (audience eatsnow-internal) with the internal.profiles.read scope; this
 * controller additionally requires the client to be listed. Any other caller receives 403.
 */
@RestController
public class InternalProfileController {

    public record ContactResponse(UUID profileId, String displayName, String email) { }

    private final GetProfileUseCase getProfileUseCase;
    private final Set<String> allowedClients;

    public InternalProfileController(GetProfileUseCase getProfileUseCase,
                                     @Value("${eatsnow.internal.allowed-clients}") Set<String> allowedClients) {
        this.getProfileUseCase = getProfileUseCase;
        this.allowedClients = allowedClients;
    }

    @GetMapping("/internal/v1/profiles/{profileId}/contact")
    public ResponseEntity<ContactResponse> contact(@PathVariable UUID profileId, Authentication caller) {
        // For client-credentials tokens the subject is the client id.
        if (caller == null || !allowedClients.contains(caller.getName())) {
            throw new ForbiddenException("Only an allowed internal service can call this endpoint");
        }
        Profile profile = getProfileUseCase.getProfileById(profileId);                // 404 NOT_FOUND when absent
        return ResponseEntity.ok(new ContactResponse(profile.id(), profile.displayName(), profile.email()));
    }
}
