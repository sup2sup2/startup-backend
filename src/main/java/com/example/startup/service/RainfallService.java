package com.example.startup.service;

import com.example.startup.cache.CacheStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RainfallService {
    private static final String CACHE_KEY = "seoul:rainfall:v1";
    private static final Duration LAST_KNOWN_MAX_AGE = Duration.ofMinutes(15);

    private final CacheStore cacheStore;
    private final String apiUrl;
    private final Duration cacheTtl;
    private final HttpClient httpClient;
    private final Object refreshLock = new Object();
    private final AtomicReference<CachedRainfall> lastKnown = new AtomicReference<>();

    public RainfallService(CacheStore cacheStore,
            @Value("${seoul.api.base-url}") String baseUrl,
            @Value("${seoul.api.key}") String apiKey,
            @Value("${app.cache.rainfall-ttl}") Duration cacheTtl) {
        this.cacheStore = cacheStore;
        this.apiUrl = baseUrl + "/" + apiKey + "/json/ListRainfallService/1/25/";
        this.cacheTtl = cacheTtl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public String getRainfallJson() {
        return cacheStore.get(CACHE_KEY)
                .map(this::remember)
                .orElseGet(this::refreshRainfall);
    }

    private String refreshRainfall() {
        synchronized (refreshLock) {
            var cached = cacheStore.get(CACHE_KEY);
            if (cached.isPresent()) {
                return remember(cached.get());
            }

            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("Seoul API returned " + response.statusCode());
                }

                String body = response.body();
                cacheStore.put(CACHE_KEY, body, cacheTtl);
                return remember(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return staleOrThrow(exception);
            } catch (Exception exception) {
                return staleOrThrow(exception);
            }
        }
    }

    private String remember(String json) {
        lastKnown.set(new CachedRainfall(json, Instant.now()));
        return json;
    }

    private String staleOrThrow(Exception cause) {
        CachedRainfall stale = lastKnown.get();
        if (stale != null && stale.fetchedAt().plus(LAST_KNOWN_MAX_AGE).isAfter(Instant.now())) {
            return stale.json();
        }
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "서울시 강우 정보를 불러오지 못했습니다.", cause);
    }

    private record CachedRainfall(String json, Instant fetchedAt) {
    }
}
