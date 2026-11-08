package com.eatsnow.identity.infrastructure.config.security;

import com.eatsnow.common.domain.model.Role;
import com.eatsnow.identity.domain.model.Profile;
import com.eatsnow.identity.domain.port.out.ProfileRepositoryPort;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Identity is the OIDC provider of EatsNow (ADR 005): Spring Authorization Server with the authorization-code flow and
 * PKCE for people, client credentials for services, and signed JWT access tokens validated locally by the Gateway and
 * every service (Spec 3.1).
 */
@Configuration
public class AuthorizationServerConfig {

    /** Audience of tokens issued to people; the Gateway and services accept only this one (Spec 3.1). */
    public static final String API_AUDIENCE = "eatsnow-api";
    /** Audience of client-credentials tokens; accepted only by internal endpoints (Spec 3.7). */
    public static final String INTERNAL_AUDIENCE = "eatsnow-internal";
    public static final String INTERNAL_PROFILES_SCOPE = "internal.profiles.read";

    static final String WEB_CLIENT_ID = "eatsnow-web";
    static final String CLI_CLIENT_ID = "eatsnow-cli";
    static final String NOTIFICATIONS_CLIENT_ID = "notifications";

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofHours(8);

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults());                    // discovery, userinfo, RP-initiated logout
        http
                // Browsers that are not logged in yet are sent to the Identity login page.
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));  // userinfo
        return http.build();
    }

    /**
     * Clients are part of the deployment, so they live in configuration rather than in a table. The IDs are fixed
     * because stored authorizations reference them across restarts.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(
            PasswordEncoder passwordEncoder,
            @Value("${eatsnow.identity.clients.web.redirect-uris}") String webRedirectUris,
            @Value("${eatsnow.identity.clients.web.post-logout-redirect-uri}") String webPostLogoutRedirectUri,
            @Value("${eatsnow.identity.clients.cli.redirect-uris}") String cliRedirectUris,
            @Value("${eatsnow.identity.clients.cli.secret}") String cliSecret,
            @Value("${eatsnow.identity.clients.notifications.secret}") String notificationsSecret) {

        // Browser app: public client, PKCE required. Spring Authorization Server issues no refresh token to public
        // clients (OAuth 2.1); the app renews by redirecting while the Identity login session is alive (ADR 005).
        RegisteredClient web = RegisteredClient.withId(WEB_CLIENT_ID)
                .clientId(WEB_CLIENT_ID)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUris(uris -> uris.addAll(split(webRedirectUris)))
                .postLogoutRedirectUri(webPostLogoutRedirectUri)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(ACCESS_TOKEN_TTL).build())
                .build();

        // Scripts, Postman and load tests: confidential client with PKCE and rotating refresh tokens.
        RegisteredClient cli = RegisteredClient.withId(CLI_CLIENT_ID)
                .clientId(CLI_CLIENT_ID)
                .clientSecret(passwordEncoder.encode(cliSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(split(cliRedirectUris)))
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                        .refreshTokenTimeToLive(REFRESH_TOKEN_TTL)
                        .reuseRefreshTokens(false)                   // rotate on every use (ADR 005)
                        .build())
                .build();

        // Service-to-service: Notifications reads customer contact data (Spec 3.7).
        RegisteredClient notifications = RegisteredClient.withId(NOTIFICATIONS_CLIENT_ID)
                .clientId(NOTIFICATIONS_CLIENT_ID)
                .clientSecret(passwordEncoder.encode(notificationsSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(INTERNAL_PROFILES_SCOPE)
                .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(ACCESS_TOKEN_TTL).build())
                .build();

        return new InMemoryRegisteredClientRepository(web, cli, notifications);
    }

    /** Codes and tokens survive restarts, and deactivation can revoke a user's refresh tokens (Spec 3.2). */
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                           RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    /**
     * Signing key generated at startup. Restarting Identity invalidates issued tokens; verifiers refresh the JWKS
     * when they meet an unknown key id. A persistent, rotated key is the production setup (ADR 005).
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
            return new ImmutableJWKSet<>(new JWKSet(rsaKey));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate the Identity signing key", ex);
        }
    }

    /** Decoder for the userinfo endpoint. The API chains build their own with audience checks (SecurityConfig). */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /** One public issuer, the Gateway, whatever host a request reached Identity through. */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings(@Value("${eatsnow.identity.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    /**
     * Shapes the tokens of Spec 3.1: `sub` is the profile id, access tokens carry roles and ownership links, and the
     * audience separates people's tokens from service tokens. Inactive profiles get no new tokens, refreshes included.
     * Claims are stored as JSON with the authorization and read back only for allowlisted types, so list claims use
     * ArrayList / singletonList rather than the immutable lists of List.of() and Stream.toList().
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(ProfileRepositoryPort profiles) {
        return context -> {
            boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
            if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
                if (accessToken) {
                    context.getClaims().audience(Collections.singletonList(INTERNAL_AUDIENCE));
                }
                return;
            }

            Profile profile = profiles.findByEmail(context.getPrincipal().getName())
                    .filter(Profile::canLogIn)
                    .orElseThrow(() -> new OAuth2AuthenticationException(
                            new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "The account is inactive", null)));

            context.getClaims().subject(profile.id().toString());
            if (accessToken) {
                context.getClaims()
                        .audience(Collections.singletonList(API_AUDIENCE))
                        .claim("roles", profile.roles().stream().map(Role::name).sorted()
                                .collect(Collectors.toCollection(ArrayList::new)));
                if (profile.restaurantId() != null) {
                    context.getClaims().claim("restaurantId", profile.restaurantId().toString());
                }
                if (profile.driverId() != null) {
                    context.getClaims().claim("driverId", profile.driverId().toString());
                }
            } else if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
                context.getClaims().claim("name", profile.displayName()).claim("email", profile.email());
            }
        };
    }

    private static List<String> split(String commaSeparated) {
        return Arrays.stream(commaSeparated.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
