package com.example.startup.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.cache.redis.enabled", havingValue = "true")
public class RedisConnectionVerifier implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RedisConnectionVerifier.class);

    private final StringRedisTemplate redisTemplate;

    public RedisConnectionVerifier(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            log.info("Redis cache connection verified (PING={})", connection.ping());
        } catch (RuntimeException exception) {
            log.warn("Redis cache connection check failed. Requests will use the source fallback.", exception);
        }
    }
}
