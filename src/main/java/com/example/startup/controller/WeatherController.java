package com.example.startup.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@CrossOrigin // 프론트엔드 호출 허용 (개발용)
public class WeatherController {

    // 🌟 application.properties의 seoul.api.key 값을 자동으로 주입
    @Value("${seoul.api.key}")
    private String seoulApiKey;

    // 🌟 프론트엔드가 부를 새 엔드포인트
    @GetMapping("/api/weather/rainfall")
    public String getRainfall() {
        // 서울시 API 호출 URL 조립
        String url = "http://openAPI.seoul.go.kr:8088/" 
                   + seoulApiKey 
                   + "/json/ListRainfallService/1/25/";

        // RestTemplate로 서울시에 GET 요청 → 받은 JSON 그대로 반환
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(url, String.class);
    }
}