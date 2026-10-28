package com.eatsnow.identity.application.service;

import com.eatsnow.common.domain.exception.EmailAlreadyRegisteredException;
import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.common.domain.exception.InvalidRoleLinkException;
import com.eatsnow.common.domain.exception.InvalidTransitionException;
import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminProfileServiceTest {

    @Mock
    private ProfileRepositoryPort profileRepository;

    @Mock
    private IdempotencyStoragePort idempotencyStorage;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminProfileService adminProfileService;

    private final UUID adminId = UUID.fromString("00000000-0000-4000-8000-000000000099");
    private final UUID targetProfileId = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @BeforeEach
    void setUp() {
        adminProfileService = new AdminProfileService(profileRepository, idempotencyStorage, passwordEncoder);
    }

    @Test
    @DisplayName("Should list profiles with bounds and delegation")
    void testListProfiles() {
        PageResult<Profile> expected = new PageResult<>(List.of(), null);
        when(profileRepository.findAll(20, null, Role.DRIVER)).thenReturn(expected);

        PageResult<Profile> result = adminProfileService.listProfiles(20, null, Role.DRIVER);

        assertNotNull(result);
        verify(profileRepository).findAll(20, null, Role.DRIVER);
    }

    @Test
    @DisplayName("Should create DRIVER profile and auto-generate driverId (Spec 3.2)")
    void testCreateProfile_Driver_GeneratesDriverId() {
        when(profileRepository.existsByEmail("sam@example.test")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashedPw");
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Profile profile = adminProfileService.createProfile(
                adminId,
                "Sam",
                "sam@example.test",
                Set.of(Role.DRIVER),
                null,
                null,
                null
        );

        assertNotNull(profile);
        assertEquals("Sam", profile.displayName());
        assertEquals("sam@example.test", profile.email());
        assertTrue(profile.roles().contains(Role.DRIVER));
        assertNotNull(profile.driverId(), "driverId must be automatically generated");
        assertNull(profile.restaurantId());
        assertTrue(profile.active());
    }

    @Test
    @DisplayName("Should create RESTAURANT profile with restaurantId")
    void testCreateProfile_Restaurant_Success() {
        UUID restaurantId = UUID.randomUUID();
        when(profileRepository.existsByEmail("burger@example.test")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashedPw");
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Profile profile = adminProfileService.createProfile(
                adminId,
                "Burger Joint",
                "burger@example.test",
                Set.of(Role.RESTAURANT),
                restaurantId,
                null,
                null
        );

        assertNotNull(profile);
        assertEquals(restaurantId, profile.restaurantId());
        assertNull(profile.driverId());
    }

    @Test
    @DisplayName("Should throw 422 InvalidRoleLinkException when RESTAURANT role has no restaurantId (Spec 3.2)")
    void testCreateProfile_Restaurant_MissingId_Throws422() {
        assertThrows(InvalidRoleLinkException.class, () ->
                adminProfileService.createProfile(
                        adminId,
                        "Burger Joint",
                        "burger@example.test",
                        Set.of(Role.RESTAURANT),
                        null,
                        null,
                        null
                )
        );
    }

    @Test
    @DisplayName("Should throw 422 InvalidRoleLinkException when restaurantId passed without RESTAURANT role (Spec 3.2)")
    void testCreateProfile_RestaurantIdWithoutRole_Throws422() {
        UUID restaurantId = UUID.randomUUID();
        assertThrows(InvalidRoleLinkException.class, () ->
                adminProfileService.createProfile(
                        adminId,
                        "John",
                        "john@example.test",
                        Set.of(Role.USER),
                        restaurantId,
                        null,
                        null
                )
        );
    }

    @Test
    @DisplayName("Should throw 409 EmailAlreadyRegisteredException on duplicate email (Spec 3.2)")
    void testCreateProfile_DuplicateEmail_Throws409() {
        when(profileRepository.existsByEmail("alex@example.test")).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class, () ->
                adminProfileService.createProfile(
                        adminId,
                        "Alex",
                        "alex@example.test",
                        Set.of(Role.USER),
                        null,
                        null,
                        null
                )
        );
    }

    @Test
    @DisplayName("Should throw 400 InvalidRequestException on invalid email format")
    void testCreateProfile_InvalidEmail_Throws400() {
        assertThrows(InvalidRequestException.class, () ->
                adminProfileService.createProfile(
                        adminId,
                        "Alex",
                        "invalid-email",
                        Set.of(Role.USER),
                        null,
                        null,
                        null
                )
        );
    }

    @Test
    @DisplayName("Should update profile active status successfully")
    void testUpdateProfileStatus_Success() {
        Profile existing = new Profile(
                targetProfileId, "Alex", "alex@example.test", Set.of(Role.USER),
                null, null, "hash", true, Instant.now(), Instant.now()
        );
        when(profileRepository.findById(targetProfileId)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Profile updated = adminProfileService.updateProfileStatus(adminId, targetProfileId, false, null, null);

        assertFalse(updated.active());
    }

    @Test
    @DisplayName("Should throw 409 InvalidTransitionException when ADMIN tries to deactivate their own profile (Spec 3.2)")
    void testUpdateProfileStatus_AdminDeactivatesSelf_Throws409() {
        assertThrows(InvalidTransitionException.class, () ->
                adminProfileService.updateProfileStatus(adminId, adminId, false, null, null));
    }

    @Test
    @DisplayName("Should allow ADMIN to update their own profile if keeping active: true")
    void testUpdateProfileStatus_AdminKeepsSelfActive_Allowed() {
        Profile adminProfile = new Profile(
                adminId, "Admin", "admin@eatsnow.test", Set.of(Role.ADMIN),
                null, null, "hash", true, Instant.now(), Instant.now()
        );
        when(profileRepository.findById(adminId)).thenReturn(Optional.of(adminProfile));
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Profile updated = adminProfileService.updateProfileStatus(adminId, adminId, true, null, null);

        assertTrue(updated.active());
    }

    @Test
    @DisplayName("Should clamp limit parameters between 1 and 100")
    void testListProfiles_LimitClamping() {
        when(profileRepository.findAll(anyInt(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), null));

        // Limit <= 0 clamped to 1
        adminProfileService.listProfiles(0, null, null);
        verify(profileRepository).findAll(1, null, null);

        // Limit > 100 clamped to 100
        adminProfileService.listProfiles(250, null, null);
        verify(profileRepository).findAll(100, null, null);
    }

    @Test
    @DisplayName("Should normalize email to lowercase when creating profile")
    void testCreateProfile_EmailNormalized() {
        when(profileRepository.existsByEmail("alex@example.test")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Profile created = adminProfileService.createProfile(
                adminId, "Alex", "ALEX@EXAMPLE.TEST", Set.of(Role.USER), null, null, null
        );

        assertEquals("alex@example.test", created.email(), "Email must be normalized to lowercase");
    }

    @Test
    @DisplayName("Should reject blank or excessively long display names")
    void testCreateProfile_InvalidDisplayName() {
        // Blank
        assertThrows(InvalidRequestException.class, () ->
                adminProfileService.createProfile(adminId, "   ", "a@b.com", Set.of(Role.USER), null, null, null));

        // Over 100 chars
        String longName = "A".repeat(101);
        assertThrows(InvalidRequestException.class, () ->
                adminProfileService.createProfile(adminId, longName, "a@b.com", Set.of(Role.USER), null, null, null));
    }

    @Test
    @DisplayName("Should reject empty roles set")
    void testCreateProfile_EmptyRoles_ThrowsException() {
        assertThrows(InvalidRequestException.class, () ->
                adminProfileService.createProfile(adminId, "Name", "test@b.com", Set.of(), null, null, null));
    }

    @Test
    @DisplayName("Should throw 404 EntityNotFoundException when profile to update does not exist")
    void testUpdateProfileStatus_NotFound_Throws404() {
        when(profileRepository.findById(targetProfileId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
                adminProfileService.updateProfileStatus(adminId, targetProfileId, false, null, null));
    }
}
