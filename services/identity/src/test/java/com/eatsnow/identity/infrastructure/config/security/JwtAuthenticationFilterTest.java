package com.eatsnow.identity.infrastructure.config.security;

import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.infrastructure.adapter.out.crypto.RsaTokenIssuer;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private RsaTokenIssuer tokenIssuer;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(tokenIssuer);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should populate SecurityContext when valid Bearer token is provided")
    void testValidBearerToken() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .claim("email", "chef@resto.test")
                .claim("roles", List.of("RESTAURANT"))
                .claim("restaurantId", restaurantId.toString())
                .build();

        request.addHeader("Authorization", "Bearer valid.jwt.token");
        when(tokenIssuer.validateAndParse("valid.jwt.token")).thenReturn(claims);

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.isAuthenticated());
        assertInstanceOf(AuthenticatedUser.class, auth.getPrincipal());

        AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
        assertEquals(userId, user.id());
        assertEquals("chef@resto.test", user.email());
        assertEquals(restaurantId, user.restaurantId());
        assertTrue(user.roles().contains(Role.RESTAURANT));
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_RESTAURANT")));
    }

    @Test
    @DisplayName("Should not authenticate when Bearer token is invalid or expired")
    void testInvalidBearerToken() throws Exception {
        request.addHeader("Authorization", "Bearer invalid.token");
        when(tokenIssuer.validateAndParse("invalid.token")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should authenticate using Gateway forwarded headers when no Bearer token is present")
    void testGatewayForwardedHeaders() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();

        request.addHeader("X-User-Id", userId.toString());
        request.addHeader("X-User-Roles", "DRIVER,USER");
        request.addHeader("X-User-Email", "driver@eatsnow.test");
        request.addHeader("X-Driver-Id", driverId.toString());

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
        assertEquals(userId, user.id());
        assertEquals(driverId, user.driverId());
        assertTrue(user.roles().contains(Role.DRIVER));
        assertTrue(user.roles().contains(Role.USER));
    }

    @Test
    @DisplayName("Should gracefully ignore malformed X-User-Id header")
    void testMalformedGatewayHeader() throws Exception {
        request.addHeader("X-User-Id", "not-a-valid-uuid");

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should proceed without authentication when no auth headers exist")
    void testNoHeaders() throws Exception {
        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
