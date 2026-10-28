package com.eatsnow.identity.infrastructure.adapter.out.persistence;

import com.eatsnow.common.domain.model.PageResult;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.infrastructure.adapter.out.persistence.entity.ProfileEntity;
import com.eatsnow.identity.infrastructure.adapter.out.persistence.repository.SpringDataProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostgresProfileRepositoryAdapterTest {

    @Mock
    private SpringDataProfileRepository repository;

    private PostgresProfileRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PostgresProfileRepositoryAdapter(repository);
    }

    @Test
    @DisplayName("findById should map entity with comma-separated roles to domain Profile")
    void testFindById() {
        UUID id = UUID.randomUUID();
        ProfileEntity entity = new ProfileEntity(
                id, "Alice", "alice@example.test", "USER,ADMIN",
                null, null, "hash", true, Instant.now(), Instant.now()
        );
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        Optional<Profile> result = adapter.findById(id);

        assertTrue(result.isPresent());
        Profile profile = result.get();
        assertEquals(id, profile.id());
        assertEquals("Alice", profile.displayName());
        assertEquals("alice@example.test", profile.email());
        assertEquals(2, profile.roles().size());
        assertTrue(profile.roles().contains(Role.USER));
        assertTrue(profile.roles().contains(Role.ADMIN));
    }

    @Test
    @DisplayName("findByEmail should query repository and return empty when not found")
    void testFindByEmail_NotFound() {
        when(repository.findByEmail("missing@example.test")).thenReturn(Optional.empty());

        Optional<Profile> result = adapter.findByEmail("missing@example.test");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("existsByEmail should delegate to repository")
    void testExistsByEmail() {
        when(repository.existsByEmail("alex@example.test")).thenReturn(true);
        assertTrue(adapter.existsByEmail("alex@example.test"));

        when(repository.existsByEmail("unknown@example.test")).thenReturn(false);
        assertFalse(adapter.existsByEmail("unknown@example.test"));
    }

    @Test
    @DisplayName("save should map domain model to entity and back")
    void testSave() {
        UUID id = UUID.randomUUID();
        Profile profile = new Profile(
                id, "Sam", "sam@example.test", Set.of(Role.DRIVER),
                null, UUID.randomUUID(), "hash", true, Instant.now(), Instant.now()
        );

        when(repository.save(any(ProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Profile saved = adapter.save(profile);

        assertNotNull(saved);
        assertEquals(id, saved.id());
        assertEquals("Sam", saved.displayName());

        ArgumentCaptor<ProfileEntity> captor = ArgumentCaptor.forClass(ProfileEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("DRIVER", captor.getValue().getRoles());
    }

    @Test
    @DisplayName("findAll should return nextCursor when entities count exceeds limit")
    void testFindAll_WithMorePages() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        List<ProfileEntity> threeEntities = List.of(
                new ProfileEntity(id1, "User 1", "u1@test.com", "USER", null, null, "h", true, Instant.now(), Instant.now()),
                new ProfileEntity(id2, "User 2", "u2@test.com", "USER", null, null, "h", true, Instant.now(), Instant.now()),
                new ProfileEntity(id3, "User 3", "u3@test.com", "USER", null, null, "h", true, Instant.now(), Instant.now())
        );

        when(repository.findWithCursorAndRole(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(threeEntities);

        PageResult<Profile> page = adapter.findAll(2, null, null);

        assertEquals(2, page.items().size());
        assertEquals(id2.toString(), page.nextCursor(), "nextCursor must be the ID of the last item in the page");
    }

    @Test
    @DisplayName("findAll should return null nextCursor when entities count is within limit")
    void testFindAll_LastPage() {
        UUID id1 = UUID.randomUUID();
        List<ProfileEntity> singleEntity = List.of(
                new ProfileEntity(id1, "User 1", "u1@test.com", "USER", null, null, "h", true, Instant.now(), Instant.now())
        );

        when(repository.findWithCursorAndRole(eq("DRIVER"), eq(null), any(Pageable.class)))
                .thenReturn(singleEntity);

        PageResult<Profile> page = adapter.findAll(10, null, Role.DRIVER);

        assertEquals(1, page.items().size());
        assertNull(page.nextCursor());
    }

    @Test
    @DisplayName("findAll should parse valid cursor UUID and ignore malformed cursor")
    void testFindAll_CursorParsing() {
        UUID validCursor = UUID.randomUUID();
        when(repository.findWithCursorAndRole(eq(null), eq(validCursor), any(Pageable.class)))
                .thenReturn(List.of());

        adapter.findAll(10, validCursor.toString(), null);
        verify(repository).findWithCursorAndRole(eq(null), eq(validCursor), any(Pageable.class));

        // Malformed cursor should fall back to null cursorId
        when(repository.findWithCursorAndRole(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(List.of());

        adapter.findAll(10, "not-a-valid-uuid", null);
        verify(repository).findWithCursorAndRole(eq(null), eq(null), any(Pageable.class));
    }
}
