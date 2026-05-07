package com.example.startup.repository;

import com.example.startup.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    
    // 🌟 핵심: 프론트에서 넘어온 '아이디'로 DB에서 회원을 찾아오는 마법의 코드
    Optional<Member> findByLoginId(String loginId);
}