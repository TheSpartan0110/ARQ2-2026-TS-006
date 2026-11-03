package com.eatsnow.orders.infrastructure.config.security;

import com.eatsnow.security.JwksKeySource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Public-key source of the Catalog quote tokens. Identity keys are handled by the shared security module. */
@Configuration
public class JwksConfig {

    @Bean("catalogJwks")
    public JwksKeySource catalogJwks(@Value("${eatsnow.jwks.catalog-url}") String url) {
        return new JwksKeySource(url);
    }
}
