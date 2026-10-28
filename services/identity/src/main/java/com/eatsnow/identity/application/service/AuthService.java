package com.eatsnow.identity.application.service;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.identity.domain.model.IssuedToken;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.in.AuthenticateUseCase;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import com.eatsnow.identity.domain.port.out.TokenIssuerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Service authenticating user credentials and issuing JWT access tokens (Spec 3.1 & ADR 005).
 */
@Service
public class AuthService implements AuthenticateUseCase {

    private final ProfileRepositoryPort profileRepository;
    private final TokenIssuerPort tokenIssuer;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            ProfileRepositoryPort profileRepository,
            TokenIssuerPort tokenIssuer,
            PasswordEncoder passwordEncoder
    ) {
        this.profileRepository = profileRepository;
        this.tokenIssuer = tokenIssuer;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public IssuedToken authenticate(String email, String password) {
        Objects.requireNonNull(email, "email cannot be null");
        Objects.requireNonNull(password, "password cannot be null");

        String normalizedEmail = email.trim().toLowerCase();
        Profile profile = profileRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new EntityNotFoundException("Invalid credentials"));

        if (!passwordEncoder.matches(password, profile.passwordHash())) {
            throw new InvalidRequestException("Invalid credentials");
        }

        // Spec 3.2: Inactive profiles cannot log in
        if (!profile.active()) {
            throw new InvalidRequestException("Account is deactivated");
        }

        return tokenIssuer.issueToken(profile);
    }
}
