package com.example.jobrec.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis is configured for LRU eviction at the server level:
 *   maxmemory 256mb
 *   maxmemory-policy allkeys-lru
 *
 * Application code does not set TTL; when memory is full, Redis evicts
 * least-recently-used keys automatically.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
