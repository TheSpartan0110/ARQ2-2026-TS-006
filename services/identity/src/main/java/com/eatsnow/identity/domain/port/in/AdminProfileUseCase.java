package com.eatsnow.identity.domain.port.in;

import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.Profile;

import java.util.Set;
import java.util.UUID;

/**
 * Inbound port for administrative profile management (Spec 3.2).
 */
public interface AdminProfileUseCase {

    PageResult<Profile> listProfiles(int limit, String cursor, Role roleFilter);

    Profile createProfile(UUID adminId, String displayName, String email, Set<Role> roles, UUID restaurantId, String idempotencyKey, String rawBody);

    Profile updateProfileStatus(UUID adminId, UUID profileId, boolean active, String idempotencyKey, String rawBody);
}
