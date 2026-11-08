package com.eatsnow.identity.application.service;

import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.out.ActivationTokenRepositoryPort;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivationServiceTest {

    private static final UUID SAM = UUID.fromString("00000000-0000-4000-8000-000000000021");
    private static final String TOKEN = "raw-activation-token";
    private static final String TOKEN_HASH = ProfileRules.sha256(TOKEN);

    @Mock
    private ActivationTokenRepositoryPort activationTokens;

    @Mock
    private ProfileRepositoryPort profileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private ActivationService activationService;

    @BeforeEach
    void setUp() {
        activationService = new ActivationService(activationTokens, profileRepository, passwordEncoder);
    }

    private Profile pendingSam() {
        return new Profile(SAM, "Sam", "sam@example.test", Set.of(Role.DRIVER), null, UUID.randomUUID(),
                null, true, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("A valid link sets the password once and the account can log in (Spec 3.2)")
    void activatesWithAValidLink() {
        when(activationTokens.findUsableProfileId(eq(TOKEN_HASH), any())).thenReturn(Optional.of(SAM));
        when(activationTokens.markUsed(eq(TOKEN_HASH), any())).thenReturn(true);
        when(profileRepository.findById(SAM)).thenReturn(Optional.of(pendingSam()));
        when(passwordEncoder.encode("new-password")).thenReturn("bcrypt-hash");

        activationService.activate(TOKEN, "new-password");

        ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(saved.capture());
        assertEquals("bcrypt-hash", saved.getValue().passwordHash());
        assertTrue(saved.getValue().canLogIn());
    }

    @Test
    @DisplayName("Unknown, expired or used links are rejected without changing any profile")
    void rejectsUnusableLinks() {
        when(activationTokens.findUsableProfileId(eq(TOKEN_HASH), any())).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class, () -> activationService.activate(TOKEN, "new-password"));
        assertFalse(activationService.isUsable(TOKEN));
        verify(profileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Only one of two concurrent activations with the same link succeeds")
    void rejectsTheLosingConcurrentActivation() {
        when(activationTokens.findUsableProfileId(eq(TOKEN_HASH), any())).thenReturn(Optional.of(SAM));
        when(activationTokens.markUsed(eq(TOKEN_HASH), any())).thenReturn(false);

        assertThrows(InvalidRequestException.class, () -> activationService.activate(TOKEN, "new-password"));
        verify(profileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Weak passwords are rejected before the link is consumed")
    void rejectsWeakPasswordWithoutConsumingTheLink() {
        assertThrows(InvalidRequestException.class, () -> activationService.activate(TOKEN, "short"));
        verifyNoInteractions(activationTokens);
    }

    @Test
    @DisplayName("Only the token hash is looked up, never the raw token")
    void looksUpByHash() {
        when(activationTokens.findUsableProfileId(eq(TOKEN_HASH), any())).thenReturn(Optional.of(SAM));

        assertTrue(activationService.isUsable(TOKEN));
        verify(activationTokens, never()).findUsableProfileId(eq(TOKEN), any());
    }
}
