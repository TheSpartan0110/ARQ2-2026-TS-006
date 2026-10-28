package com.eatsnow.identity.domain.port.in;

import com.eatsnow.identity.domain.model.Profile;

import java.util.UUID;

/**
 * Inbound port for users to update their own profile display name (Spec 3.2).
 */
public interface UpdateMyProfileUseCase {
    Profile updateDisplayName(UUID id, String displayName, String idempotencyKey, String rawBody);
}
