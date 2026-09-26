package com.example.startup.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import jakarta.persistence.Column;

@Entity
@Table(indexes = {
        @Index(name = "idx_report_created_at", columnList = "created_at"),
        @Index(name = "idx_report_login_created", columnList = "login_id, created_at")
})
@Getter
@Setter
public class Report {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 신고 번호 (자동 증가)

    private Double latitude;  // 위도 (위치)
    private Double longitude; // 경도 (위치)

    private String imageUrl; // 찍은 하수구 사진 URL
    
    @Column(length = 100)
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now(); // 신고된 시간
    
    @Column(name = "login_id")
    private String loginId;
    
}
