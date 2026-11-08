package com.eatsnow.identity.application.service;

import com.eatsnow.common.domain.exception.EmailAlreadyRegisteredException;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.in.RegisterCustomerUseCase;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Customer self-registration (Spec 3.1 & ADR 005). The role is always USER: no public flow can grant other roles.
 */
@Service
public class RegistrationService implements RegisterCustomerUseCase {

    private final ProfileRepositoryPort profileRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(ProfileRepositoryPort profileRepository, PasswordEncoder passwordEncoder) {
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public Profile register(String displayName, String email, String rawPassword) {
        String name = ProfileRules.validDisplayName(displayName);
        String normalizedEmail = ProfileRules.normalizeEmail(email);
        ProfileRules.validatePassword(rawPassword);

        if (profileRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException("Email already registered: " + normalizedEmail);
        }

        Instant now = Instant.now();
        return profileRepository.save(new Profile(UUID.randomUUID(), name, normalizedEmail, Set.of(Role.USER),
                null, null, passwordEncoder.encode(rawPassword), true, now, now));
    }
}
