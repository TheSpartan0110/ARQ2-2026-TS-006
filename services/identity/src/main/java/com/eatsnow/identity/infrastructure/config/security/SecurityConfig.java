package com.eatsnow.identity.infrastructure.config.security;

import com.eatsnow.common.web.dto.ErrorResponseDto;
import com.eatsnow.common.web.filter.CorrelationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * Security of Identity's own endpoints. The OIDC endpoints have their own chain (AuthorizationServerConfig).
 * - /api/**: people's access tokens (audience eatsnow-api), roles from the `roles` claim (Spec 3.1 & 3.2).
 * - /internal/**: client-credentials tokens (audience eatsnow-internal) with the required scope (Spec 3.7).
 * - everything else: the login, registration and activation pages, with a session and CSRF protection.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JWKSource<SecurityContext> jwkSource,
                                                      @Value("${eatsnow.identity.issuer}") String issuer) throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);

        statelessJson(http.securityMatcher("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(decoder(jwkSource, issuer, AuthorizationServerConfig.API_AUDIENCE))
                                .jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, ex) -> unauthenticated(response))
                        .accessDeniedHandler((request, response, ex) -> forbidden(response)));
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http, JWKSource<SecurityContext> jwkSource,
                                                           @Value("${eatsnow.identity.issuer}") String issuer) throws Exception {
        statelessJson(http.securityMatcher("/internal/**"))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasAuthority("SCOPE_" + AuthorizationServerConfig.INTERNAL_PROFILES_SCOPE))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(decoder(jwkSource, issuer, AuthorizationServerConfig.INTERNAL_AUDIENCE)))
                        .authenticationEntryPoint((request, response, ex) -> unauthenticated(response))
                        .accessDeniedHandler((request, response, ex) -> forbidden(response)));
        return http.build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain loginPagesSecurityFilterChain(
            HttpSecurity http,
            @Value("${eatsnow.identity.clients.web.post-logout-redirect-uri}") String webAppUrl) throws Exception {
        // Only the authorization server chain saves the request to resume after login (/oauth2/authorize). If this
        // chain saved too, any stray request (a browser probing /.well-known/..., a favicon) would overwrite it and
        // the login would land on that URL instead of returning to the app.
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(request -> false);
        http
                .requestCache(cache -> cache.requestCache(requestCache))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/activate", "/css/**", "/error").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                // A login that did not start from an app (for example right after registering) goes to the web app.
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl(webAppUrl).permitAll());
        return http.build();
    }

    private HttpSecurity statelessJson(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }

    /** Signature, expiry and issuer as usual, plus the audience that keeps people's and services' tokens apart. */
    static JwtDecoder decoder(JWKSource<SecurityContext> jwkSource, String issuer, String audience) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience))));
        return decoder;
    }

    private void unauthenticated(HttpServletResponse response) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED",
                "Authentication token is missing, expired, or invalid");
    }

    private void forbidden(HttpServletResponse response) throws IOException {
        writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Access is denied for this role");
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorResponseDto(code, message, CorrelationContext.getCurrentCorrelationId(), null));
    }
}
