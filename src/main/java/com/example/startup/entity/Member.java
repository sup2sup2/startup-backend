package com.example.startup.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 회원 고유 번호 (자동 증가)

    @Column(unique = true, nullable = false)
    private String loginId; // 로그인 아이디 (중복 불가)
    
    private int points = 0;

    @Column(nullable = false)
    private String password; // 비밀번호

    private String name; // 사용자 닉네임 또는 이름

    private LocalDateTime createdAt = LocalDateTime.now(); // 가입 시간
    
    private int failedAttempts = 0; // 틀린 횟수 (기본값 0)
    
    private LocalDateTime lockTime; // 계정이 잠긴 시간 (이 시간 전까지는 로그인 불가)
}

