package com.example.startup.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 서버의 모든 API 주소에 대해
                .allowedOriginPatterns("*") // 모든 프론트엔드 접속(IP)을 허용
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 🌟 모든 메서드 완벽 허용
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600); // 사전 요청(Preflight) 결과를 1시간 동안 캐시
    }
}