package com.example.startup.config;

import com.example.startup.cache.CacheStore;
import com.example.startup.cache.NoOpCacheStore;
import com.example.startup.cache.RedisCacheStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class CacheConfig {
    @Bean
    @ConditionalOnProperty(name = "app.cache.redis.enabled", havingValue = "true")
    CacheStore redisCacheStore(StringRedisTemplate redisTemplate) {
        return new RedisCacheStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(CacheStore.class)
    CacheStore noOpCacheStore() {
        return new NoOpCacheStore();
    }
}
