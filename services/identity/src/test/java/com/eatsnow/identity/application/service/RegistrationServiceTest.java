package com.eatsnow.identity.application.service;

import com.eatsnow.common.domain.exception.EmailAlreadyRegisteredException;
import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private ProfileRepositoryPort profileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(profileRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Self-registration creates an active USER profile with a hashed password (Spec 3.1)")
    void registersAUserOnly() {
        when(profileRepository.existsByEmail("nora@example.test")).thenReturn(false);
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("bcrypt-hash");
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Profile profile = registrationService.register(" Nora ", "Nora@Example.test", "s3cret-pass");

        assertEquals("Nora", profile.displayName());
        assertEquals("nora@example.test", profile.email());
        assertEquals(Set.of(Role.USER), profile.roles());
        assertNull(profile.restaurantId());
        assertNull(profile.driverId());
        assertEquals("bcrypt-hash", profile.passwordHash());
        assertTrue(profile.canLogIn());
    }

    @Test
    @DisplayName("A registered email returns 409 EMAIL_ALREADY_REGISTERED")
    void rejectsDuplicateEmail() {
        when(profileRepository.existsByEmail("alex@example.test")).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class,
                () -> registrationService.register("Alex", "alex@example.test", "password123"));
        verify(profileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Passwords must be 8 to 72 characters")
    void rejectsShortOrLongPasswords() {
        assertThrows(InvalidRequestException.class,
                () -> registrationService.register("Nora", "nora@example.test", "short"));
        assertThrows(InvalidRequestException.class,
                () -> registrationService.register("Nora", "nora@example.test", "x".repeat(73)));
        verifyNoInteractions(profileRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Invalid email or display name is rejected")
    void rejectsInvalidInput() {
        assertThrows(InvalidRequestException.class,
                () -> registrationService.register("Nora", "not-an-email", "password123"));
        assertThrows(InvalidRequestException.class,
                () -> registrationService.register("  ", "nora@example.test", "password123"));
    }
}
