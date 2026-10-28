package com.eatsnow.identity.application.service;

import com.eatsnow.common.domain.exception.EntityNotFoundException;
import com.eatsnow.common.domain.exception.IdempotencyConflictException;
import com.eatsnow.common.domain.exception.InvalidRequestException;
import com.eatsnow.common.domain.exception.RequestInProgressException;
import com.eatsnow.common.domain.model.IdempotencyEntry;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.common.domain.port.out.IdempotencyStoragePort;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepositoryPort profileRepository;

    @Mock
    private IdempotencyStoragePort idempotencyStorage;

    private ProfileService profileService;

    private final UUID userId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private Profile testProfile;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(profileRepository, idempotencyStorage);
        testProfile = new Profile(
                userId,
                "Alex",
                "alex@example.test",
                Set.of(Role.USER),
                null,
                null,
                "hash",
                true,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should return profile by id when found")
    void testGetProfileById_Found() {
        when(profileRepository.findById(userId)).thenReturn(Optional.of(testProfile));

        Profile result = profileService.getProfileById(userId);

        assertNotNull(result);
        assertEquals("Alex", result.displayName());
        assertEquals("alex@example.test", result.email());
        assertTrue(result.active());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when profile not found")
    void testGetProfileById_NotFound() {
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> profileService.getProfileById(userId));
    }

    @Test
    @DisplayName("Should update display name and complete idempotency")
    void testUpdateDisplayName_Success() {
        when(profileRepository.findById(userId)).thenReturn(Optional.of(testProfile));
        when(idempotencyStorage.find(any())).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String key = UUID.randomUUID().toString();
        Profile result = profileService.updateDisplayName(userId, "Alex Rivera", key, "{\"displayName\":\"Alex Rivera\"}");

        assertEquals("Alex Rivera", result.displayName());
        verify(idempotencyStorage).start(any(), any());
        verify(idempotencyStorage).complete(any(), any(), eq(200), any());
    }

    @Test
    @DisplayName("Should reject blank display name")
    void testUpdateDisplayName_Blank() {
        assertThrows(InvalidRequestException.class, () ->
                profileService.updateDisplayName(userId, "   ", null, null));
    }

    @Test
    @DisplayName("Should throw RequestInProgressException on concurrent idempotent request")
    void testUpdateDisplayName_InProgress() {
        String key = UUID.randomUUID().toString();
        IdempotencyEntry inProgressEntry = new IdempotencyEntry(
                "key", "hash", "IN_PROGRESS", 0, null, Instant.now()
        );
        when(idempotencyStorage.find(any())).thenReturn(Optional.of(inProgressEntry));

        assertThrows(RequestInProgressException.class, () ->
                profileService.updateDisplayName(userId, "Alex Rivera", key, "raw"));
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when request payload changes")
    void testUpdateDisplayName_Conflict() {
        String key = UUID.randomUUID().toString();
        IdempotencyEntry completedEntry = new IdempotencyEntry(
                "key", "different_hash", "COMPLETED", 200, "{}", Instant.now()
        );
        when(idempotencyStorage.find(any())).thenReturn(Optional.of(completedEntry));

        assertThrows(IdempotencyConflictException.class, () ->
                profileService.updateDisplayName(userId, "Alex Rivera", key, "raw"));
    }
}
