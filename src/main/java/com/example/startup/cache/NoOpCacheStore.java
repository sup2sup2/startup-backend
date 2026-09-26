package com.example.startup.cache;

import java.time.Duration;
import java.util.Optional;

public class NoOpCacheStore implements CacheStore {
    @Override
    public Optional<String> get(String key) {
        return Optional.empty();
    }

    @Override
    public void put(String key, String value, Duration ttl) {
    }

    @Override
    public void evict(String key) {
    }
}
