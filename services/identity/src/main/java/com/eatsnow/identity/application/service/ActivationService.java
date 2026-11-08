package com.eatsnow.identity.application.service;

import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.in.ActivateAccountUseCase;
import com.eatsnow.identity.domain.port.out.ActivationTokenRepositoryPort;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Activation of accounts created by an ADMIN (Spec 3.2): a one-time link lets the person set their password.
 */
@Service
public class ActivationService implements ActivateAccountUseCase {

    private static final String INVALID_LINK = "The activation link is invalid, expired or already used";

    private final ActivationTokenRepositoryPort activationTokens;
    private final ProfileRepositoryPort profileRepository;
    private final PasswordEncoder passwordEncoder;

    public ActivationService(ActivationTokenRepositoryPort activationTokens, ProfileRepositoryPort profileRepository,
                             PasswordEncoder passwordEncoder) {
        this.activationTokens = activationTokens;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUsable(String rawToken) {
        return rawToken != null && !rawToken.isBlank()
                && activationTokens.findUsableProfileId(ProfileRules.sha256(rawToken), Instant.now()).isPresent();
    }

    @Override
    @Transactional
    public void activate(String rawToken, String rawPassword) {
        ProfileRules.validatePassword(rawPassword);
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRequestException(INVALID_LINK);
        }
        String tokenHash = ProfileRules.sha256(rawToken);
        Instant now = Instant.now();

        UUID profileId = activationTokens.findUsableProfileId(tokenHash, now)
                .orElseThrow(() -> new InvalidRequestException(INVALID_LINK));
        if (!activationTokens.markUsed(tokenHash, now)) {          // lost a race with a concurrent activation
            throw new InvalidRequestException(INVALID_LINK);
        }
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new InvalidRequestException(INVALID_LINK));
        if (profile.isActivated()) {
            throw new InvalidRequestException(INVALID_LINK);
        }
        profileRepository.save(profile.withPasswordHash(passwordEncoder.encode(rawPassword), now));
    }
}
