package com.example.startup.service;

import com.example.startup.cache.NoOpCacheStore;
import com.example.startup.cache.CacheStore;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskServiceTests {
    @Test
    void calculatesRiskFromDistrictRainfallAndRecentReports() {
        RainfallService rainfallService = mock(RainfallService.class);
        ReportReadService reportReadService = mock(ReportReadService.class);
        when(rainfallService.getRainfallJson()).thenReturn("""
                {"ListRainfallService":{"row":[
                  {"GU_NM":"강남구","RN_10M":"7.5"},
                  {"GU_NM":"강남구","RN_10M":"5.0"}
                ]}}
                """);
        when(reportReadService.countSince(any(LocalDateTime.class))).thenReturn(3L);
        RiskService service = new RiskService(rainfallService, reportReadService,
                new NoOpCacheStore(), new ObjectMapper(), Duration.ofSeconds(20), true);

        RiskService.RiskResponse response = service.getRisk("강남구");

        assertEquals(7.5, response.rainfall10Minutes());
        assertEquals(45.0, response.hourlyEstimate());
        assertEquals(51, response.score());
        assertEquals(RiskService.RiskLevel.WARNING, response.level());
    }

    @Test
    void rejectsUnknownDistrict() {
        RiskService service = new RiskService(mock(RainfallService.class),
                mock(ReportReadService.class), new NoOpCacheStore(),
                new ObjectMapper(), Duration.ofSeconds(20), true);

        assertThrows(IllegalArgumentException.class,
                () -> service.getRisk("없는구"));
    }

    @Test
    void bypassesOnlyRiskCacheWhenDisabled() {
        RainfallService rainfallService = mock(RainfallService.class);
        ReportReadService reportReadService = mock(ReportReadService.class);
        CacheStore cacheStore = mock(CacheStore.class);
        when(rainfallService.getRainfallJson()).thenReturn(
                "{\"ListRainfallService\":{\"row\":[{\"GU_NM\":\"강남구\",\"RN_10M\":\"1.0\"}]}}");
        when(reportReadService.countSince(any(LocalDateTime.class))).thenReturn(0L);
        RiskService service = new RiskService(rainfallService, reportReadService,
                cacheStore, new ObjectMapper(), Duration.ofSeconds(20), false);

        service.getRisk("강남구");
        service.getRisk("강남구");

        verify(rainfallService, times(2)).getRainfallJson();
        verify(cacheStore, never()).get(any());
        verify(cacheStore, never()).put(any(), any(), any());
    }
}
