package com.eatsnow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.observation.ObservationRegistry;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Enables bearer-token authentication when `eatsnow.security.identity-jwks-url` is set. The service declares which
 * role it requires with `eatsnow.security.required-role` (USER, RESTAURANT, DRIVER, or ANY for any active role) and
 * may exempt unauthenticated paths with `eatsnow.security.public-paths`. `eatsnow.security.issuer` is the public
 * Identity issuer (Spec 3.1).
 */
@AutoConfiguration
@ConditionalOnProperty("eatsnow.security.identity-jwks-url")
public class SecurityAutoConfiguration {

    @Bean
    public IdentityTokenVerifier identityTokenVerifier(@Value("${eatsnow.security.identity-jwks-url}") String jwksUrl,
                                                       @Value("${eatsnow.security.issuer:http://localhost:8090}") String issuer,
                                                       ObjectProvider<ObservationRegistry> observations,
                                                       ObjectProvider<MeterRegistry> meters) {
        return new IdentityTokenVerifier(new JwksKeySource(jwksUrl,
                observations.getIfAvailable(() -> ObservationRegistry.NOOP),
                meters.getIfAvailable(() -> Metrics.globalRegistry)), issuer);
    }

    @Bean
    public FilterRegistrationBean<BearerAuthenticationFilter> bearerAuthenticationFilter(
            IdentityTokenVerifier verifier, ObjectMapper mapper,
            @Value("${eatsnow.security.required-role}") String requiredRole,
            @Value("${eatsnow.security.public-paths:}") Set<String> publicPaths) {
        FilterRegistrationBean<BearerAuthenticationFilter> registration = new FilterRegistrationBean<>(
                new BearerAuthenticationFilter(verifier, mapper, requiredRole, publicPaths));
        // After the correlation filter (HIGHEST_PRECEDENCE), so error bodies carry the correlation id.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }
}
