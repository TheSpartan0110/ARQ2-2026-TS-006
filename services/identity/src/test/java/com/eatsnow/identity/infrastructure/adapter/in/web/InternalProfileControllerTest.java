package com.eatsnow.identity.infrastructure.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eatsnow.common.domain.exception.ForbiddenException;
import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.in.GetProfileUseCase;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

class InternalProfileControllerTest {

    private static final UUID PROFILE = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final GetProfileUseCase profiles = mock(GetProfileUseCase.class);
    private final InternalProfileController controller = new InternalProfileController(profiles, Set.of("notifications"));

    @Test
    void returnsOnlyTheContactFieldsToTheListedClient() {
        when(profiles.getProfileById(PROFILE)).thenReturn(new Profile(PROFILE, "Alex", "alex@example.test",
                Set.of(Role.USER), null, null, "$2a$hash", true, Instant.now(), Instant.now()));

        var response = controller.contact(PROFILE, new TestingAuthenticationToken("notifications", null)).getBody();

        assertEquals("Alex", response.displayName());
        assertEquals("alex@example.test", response.email());
    }

    @Test
    void anyOtherClientReceivesForbidden() {
        assertThrows(ForbiddenException.class,
                () -> controller.contact(PROFILE, new TestingAuthenticationToken("payments", null)));
        assertThrows(ForbiddenException.class, () -> controller.contact(PROFILE, null));
        verifyNoInteractions(profiles);
    }
}
