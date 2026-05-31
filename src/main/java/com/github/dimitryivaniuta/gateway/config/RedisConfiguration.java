package com.github.dimitryivaniuta.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Redis configuration for string counters, scores, and access-list cache values.
 */
@Configuration
public class RedisConfiguration {

    /**
     * Creates a String Redis template suitable for rate-limit keys and small cache entries.
     *
     * @param connectionFactory auto-configured reactive Redis connection factory
     * @return reactive string Redis template
     */
    @Bean
    ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }
}
