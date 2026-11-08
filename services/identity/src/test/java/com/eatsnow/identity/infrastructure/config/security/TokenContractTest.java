package com.eatsnow.identity.infrastructure.config.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/** The token shape of Spec 3.1: subject, audience, roles and ownership links, and who may receive tokens. */
class TokenContractTest {

    private static final String ISSUER = "http://localhost:8090";
    private static final UUID ELENA = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private static final UUID BISTRO = UUID.fromString("00000000-0000-4000-8000-000000000050");

    private final AuthorizationServerConfig config = new AuthorizationServerConfig();
    private final ProfileRepositoryPort profiles = mock(ProfileRepositoryPort.class);
    private final OAuth2TokenCustomizer<JwtEncodingContext> customizer = config.tokenCustomizer(profiles);
    private final JWKSource<SecurityContext> keys = config.jwkSource();

    private Profile elena(boolean active) {
        return new Profile(ELENA, "Elena", "elena@example.test", Set.of(Role.RESTAURANT), BISTRO, null,
                "$2a$hash", active, Instant.now(), Instant.now());
    }

    private JwtClaimsSet customize(AuthorizationGrantType grant, OAuth2TokenType type, String principal) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(ISSUER).subject(principal);
        customizer.customize(JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), claims)
                .principal(new TestingAuthenticationToken(principal, null))
                .authorizationGrantType(grant)
                .tokenType(type)
                .build());
        return claims.build();
    }

    @Test
    void accessTokensCarryTheProfileIdRolesAndOwnershipLinks() {
        when(profiles.findByEmail("elena@example.test")).thenReturn(Optional.of(elena(true)));

        JwtClaimsSet claims = customize(AuthorizationGrantType.AUTHORIZATION_CODE, OAuth2TokenType.ACCESS_TOKEN,
                "elena@example.test");

        assertEquals(ELENA.toString(), claims.getSubject());
        assertEquals(List.of(AuthorizationServerConfig.API_AUDIENCE), claims.getAudience());
        assertEquals(List.of("RESTAURANT"), claims.getClaim("roles"));
        assertEquals(BISTRO.toString(), claims.getClaim("restaurantId"));
        assertFalse(claims.hasClaim("driverId"));
    }

    /**
     * Claims are persisted with the authorization and read back by JdbcOAuth2AuthorizationService (refresh grant),
     * whose ObjectMapper accepts only allowlisted types. Immutable List.of() lists failed with HTTP 500 on refresh.
     */
    @Test
    void claimsSurviveTheAuthorizationStoreRoundTrip() throws Exception {
        when(profiles.findByEmail("elena@example.test")).thenReturn(Optional.of(elena(true)));
        JwtClaimsSet claims = customize(AuthorizationGrantType.AUTHORIZATION_CODE, OAuth2TokenType.ACCESS_TOKEN,
                "elena@example.test");

        ObjectMapper store = new ObjectMapper();
        store.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        store.registerModule(new OAuth2AuthorizationServerJackson2Module());
        Map<String, Object> restored = store.readValue(store.writeValueAsString(claims.getClaims()),
                new TypeReference<Map<String, Object>>() { });

        assertEquals(List.of("RESTAURANT"), restored.get("roles"));
        assertEquals(List.of(AuthorizationServerConfig.API_AUDIENCE), restored.get("aud"));
    }

    @Test
    void inactiveProfilesGetNoNewTokensNotEvenByRefresh() {
        when(profiles.findByEmail("elena@example.test")).thenReturn(Optional.of(elena(false)));

        assertThrows(OAuth2AuthenticationException.class, () -> customize(AuthorizationGrantType.REFRESH_TOKEN,
                OAuth2TokenType.ACCESS_TOKEN, "elena@example.test"));
    }

    @Test
    void serviceTokensGetTheInternalAudienceOnly() {
        JwtClaimsSet claims = customize(AuthorizationGrantType.CLIENT_CREDENTIALS, OAuth2TokenType.ACCESS_TOKEN,
                "notifications");

        assertEquals(List.of(AuthorizationServerConfig.INTERNAL_AUDIENCE), claims.getAudience());
        assertEquals("notifications", claims.getSubject());
        verifyNoInteractions(profiles);
    }

    @Test
    void apiDecoderAcceptsOnlyTheApiAudienceAndTheIssuer() {
        JwtDecoder api = SecurityConfig.decoder(keys, ISSUER, AuthorizationServerConfig.API_AUDIENCE);

        assertEquals(ELENA.toString(), api.decode(sign(ISSUER, AuthorizationServerConfig.API_AUDIENCE)).getSubject());
        assertThrows(JwtValidationException.class,
                () -> api.decode(sign(ISSUER, AuthorizationServerConfig.INTERNAL_AUDIENCE)));
        assertThrows(JwtValidationException.class,
                () -> api.decode(sign("http://evil.example", AuthorizationServerConfig.API_AUDIENCE)));
    }

    private String sign(String issuer, String audience) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(ELENA.toString())
                .audience(List.of(audience)).issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        return new NimbusJwtEncoder(keys)
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }
}
