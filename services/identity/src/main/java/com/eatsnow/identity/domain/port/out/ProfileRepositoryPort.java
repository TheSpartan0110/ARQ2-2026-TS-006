package com.eatsnow.identity.domain.port.out;

import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.Profile;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for profile persistence (Spec 3.2).
 */
public interface ProfileRepositoryPort {

    Optional<Profile> findById(UUID id);

    Optional<Profile> findByEmail(String email);

    boolean existsByEmail(String email);

    Profile save(Profile profile);

    PageResult<Profile> findAll(int limit, String cursor, Role roleFilter);
}
