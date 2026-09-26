package com.example.startup.service;

import com.example.startup.cache.CacheStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class RiskService {
    private static final Set<String> SEOUL_DISTRICTS = Set.of(
            "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구",
            "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구",
            "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구");

    private final RainfallService rainfallService;
    private final ReportReadService reportReadService;
    private final CacheStore cacheStore;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public RiskService(RainfallService rainfallService, ReportReadService reportReadService,
            CacheStore cacheStore, ObjectMapper objectMapper,
            @Value("${app.cache.risk-ttl}") Duration cacheTtl) {
        this.rainfallService = rainfallService;
        this.reportReadService = reportReadService;
        this.cacheStore = cacheStore;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    public RiskResponse getRisk(String district) {
        String normalizedDistrict = normalizeDistrict(district);
        String cacheKey = "risk:v1:" + normalizedDistrict;

        var cached = cacheStore.get(cacheKey);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), RiskResponse.class);
            } catch (Exception exception) {
                cacheStore.evict(cacheKey);
            }
        }

        double rainfall10Minutes = findRainfall(normalizedDistrict);
        double hourlyEstimate = rainfall10Minutes * 6.0;
        long recentReports = reportReadService.countSince(LocalDateTime.now().minusMinutes(10));
        int score = (int) Math.min(100,
                Math.round(hourlyEstimate) + Math.min(30, recentReports * 2));
        RiskLevel level = score >= 70 ? RiskLevel.DANGER
                : score >= 30 ? RiskLevel.WARNING : RiskLevel.NORMAL;

        RiskResponse response = new RiskResponse(
                normalizedDistrict,
                roundOneDecimal(rainfall10Minutes),
                roundOneDecimal(hourlyEstimate),
                recentReports,
                score,
                level,
                Instant.now(),
                "최근 10분 강우량과 서울시 전체 최근 신고 수를 조합한 서비스용 지표");
        try {
            cacheStore.put(cacheKey, objectMapper.writeValueAsString(response), cacheTtl);
        } catch (Exception ignored) {
            // The response is still useful even when serialization for cache fails.
        }
        return response;
    }

    private double findRainfall(String district) {
        try {
            JsonNode root = objectMapper.readTree(rainfallService.getRainfallJson());
            JsonNode rows = root.path("ListRainfallService").path("row");
            if (!rows.isArray()) {
                return 0.0;
            }

            double maximum = 0.0;
            for (JsonNode row : rows) {
                if (district.equals(row.path("GU_NM").asString())) {
                    maximum = Math.max(maximum, row.path("RN_10M").asDouble(0.0));
                }
            }
            return maximum;
        } catch (Exception exception) {
            throw new IllegalStateException("서울시 강우 응답을 해석하지 못했습니다.", exception);
        }
    }

    private String normalizeDistrict(String district) {
        if (district == null || district.isBlank()) {
            throw new IllegalArgumentException("district는 필수입니다.");
        }
        String normalized = district.trim();
        if (!SEOUL_DISTRICTS.contains(normalized)) {
            throw new IllegalArgumentException("서울시 자치구 이름을 입력해 주세요.");
        }
        return normalized;
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public enum RiskLevel {
        NORMAL, WARNING, DANGER
    }

    public record RiskResponse(String district, double rainfall10Minutes,
            double hourlyEstimate, long recentCitywideReports, int score,
            RiskLevel level, Instant calculatedAt, String basis) {
    }
}
