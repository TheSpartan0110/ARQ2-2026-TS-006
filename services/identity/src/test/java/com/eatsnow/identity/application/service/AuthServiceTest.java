package com.eatsnow.identity.application.service;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.IssuedToken;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import com.eatsnow.identity.domain.port.out.TokenIssuerPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private ProfileRepositoryPort profileRepository;

    @Mock
    private TokenIssuerPort tokenIssuer;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    private Profile activeProfile;
    private Profile inactiveProfile;

    @BeforeEach
    void setUp() {
        authService = new AuthService(profileRepository, tokenIssuer, passwordEncoder);

        activeProfile = new Profile(
                UUID.randomUUID(), "Alex", "alex@example.test", Set.of(Role.USER),
                null, null, "encodedPass", true, Instant.now(), Instant.now()
        );

        inactiveProfile = new Profile(
                UUID.randomUUID(), "Deactivated", "inactive@example.test", Set.of(Role.USER),
                null, null, "encodedPass", false, Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("Should authenticate and issue token when credentials are valid")
    void testAuthenticate_Success() {
        when(profileRepository.findByEmail("alex@example.test")).thenReturn(Optional.of(activeProfile));
        when(passwordEncoder.matches("secret123", "encodedPass")).thenReturn(true);

        IssuedToken expectedToken = new IssuedToken("jwt.token", "Bearer", 900, activeProfile.id(), activeProfile.roles(), null, null);
        when(tokenIssuer.issueToken(activeProfile)).thenReturn(expectedToken);

        IssuedToken token = authService.authenticate("alex@example.test", "secret123");

        assertNotNull(token);
        assertEquals("jwt.token", token.token());
        assertEquals(900, token.expiresIn());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when email is not found")
    void testAuthenticate_EmailNotFound() {
        when(profileRepository.findByEmail("missing@example.test")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
                authService.authenticate("missing@example.test", "pass"));
    }

    @Test
    @DisplayName("Should throw InvalidRequestException when password does not match")
    void testAuthenticate_WrongPassword() {
        when(profileRepository.findByEmail("alex@example.test")).thenReturn(Optional.of(activeProfile));
        when(passwordEncoder.matches("wrong", "encodedPass")).thenReturn(false);

        assertThrows(InvalidRequestException.class, () ->
                authService.authenticate("alex@example.test", "wrong"));
    }

    @Test
    @DisplayName("Should reject deactivated account on login attempt (Spec 3.2)")
    void testAuthenticate_InactiveProfile_ThrowsException() {
        when(profileRepository.findByEmail("inactive@example.test")).thenReturn(Optional.of(inactiveProfile));
        when(passwordEncoder.matches("secret123", "encodedPass")).thenReturn(true);

        InvalidRequestException ex = assertThrows(InvalidRequestException.class, () ->
                authService.authenticate("inactive@example.test", "secret123"));

        assertTrue(ex.getMessage().contains("deactivated"));
    }
}
