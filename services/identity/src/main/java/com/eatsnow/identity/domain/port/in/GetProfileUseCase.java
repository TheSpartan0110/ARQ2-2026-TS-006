package com.eatsnow.identity.domain.port.in;

import com.eatsnow.identity.domain.model.Profile;

import java.util.UUID;

/**
 * Inbound port to retrieve a profile by id (Spec 3.2).
 */
public interface GetProfileUseCase {
    Profile getProfileById(UUID id);
}
