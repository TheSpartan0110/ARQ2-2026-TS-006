package com.eatsnow.catalog.infrastructure.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis is only a cache (ADR 002): when it is down, every cache call must fail fast so the catalog falls back to
 * PostgreSQL within its latency target (Spec 5, Feature 2) instead of waiting on Lettuce's 60 s default timeout.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:redis}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.timeout:500ms}")
    private Duration commandTimeout;

    @Value("${spring.data.redis.connect-timeout:500ms}")
    private Duration connectTimeout;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        ClientOptions options = ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                // While disconnected, reject commands at once instead of queuing them until the reconnection.
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build();
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(commandTimeout)
                .clientOptions(options)
                .build();
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(redisHost, redisPort), client);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
