package com.eatsnow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Enables bearer-token authentication when `eatsnow.security.identity-jwks-url` is set. The service declares which
 * role it requires with `eatsnow.security.required-role` (USER, RESTAURANT or DRIVER).
 */
@AutoConfiguration
@ConditionalOnProperty("eatsnow.security.identity-jwks-url")
public class SecurityAutoConfiguration {

    @Bean
    public IdentityTokenVerifier identityTokenVerifier(@Value("${eatsnow.security.identity-jwks-url}") String jwksUrl,
                                                       ObjectProvider<ObservationRegistry> observations,
                                                       ObjectProvider<MeterRegistry> meters) {
        return new IdentityTokenVerifier(new JwksKeySource(jwksUrl,
                observations.getIfAvailable(() -> ObservationRegistry.NOOP),
                meters.getIfAvailable(() -> Metrics.globalRegistry)));
    }

    @Bean
    public FilterRegistrationBean<BearerAuthenticationFilter> bearerAuthenticationFilter(
            IdentityTokenVerifier verifier, ObjectMapper mapper,
            @Value("${eatsnow.security.required-role}") String requiredRole) {
        FilterRegistrationBean<BearerAuthenticationFilter> registration =
                new FilterRegistrationBean<>(new BearerAuthenticationFilter(verifier, mapper, requiredRole));
        // After the correlation filter (HIGHEST_PRECEDENCE), so error bodies carry the correlation id.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }
}
