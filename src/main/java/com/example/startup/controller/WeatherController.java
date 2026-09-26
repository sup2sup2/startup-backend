package com.example.startup.controller;

import com.example.startup.service.RainfallService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WeatherController {
    private final RainfallService rainfallService;

    public WeatherController(RainfallService rainfallService) {
        this.rainfallService = rainfallService;
    }

    @GetMapping("/api/weather/rainfall")
    public String getRainfall() {
        return rainfallService.getRainfallJson();
    }
}
