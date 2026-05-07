package com.example.startup.controller;

import com.example.startup.entity.Member;
import com.example.startup.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;


@RestController
@CrossOrigin(origins = {
	    "https://startup-frontend-rho.vercel.app",
	    "http://localhost:3000"
	})
@RequestMapping("/api/members")
public class MemberController {

    @Autowired
    private MemberRepository memberRepository;

    // 📝 1. 회원가입 API (/api/members/signup)
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> data) {
        String loginId = data.get("loginId");
        String password = data.get("password");
        
        // 아이디 중복 검사
        if (memberRepository.findByLoginId(loginId).isPresent()) {
            return ResponseEntity.badRequest().body("이미 존재하는 아이디입니다.");
        }

        // 새 회원 저장
        Member newMember = new Member();
        newMember.setLoginId(loginId);
        newMember.setPassword(password); // (참고: 실제 앱에서는 암호화해야 하지만, 지금은 직관적인 테스트를 위해 그냥 저장합니다!)
        
        memberRepository.save(newMember);
        return ResponseEntity.ok("회원가입 성공!");
    }

 // 🔐 2. 로그인 API (/api/members/login)
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> data) {
        String loginId = data.get("loginId");
        String password = data.get("password");

        Optional<Member> memberOpt = memberRepository.findByLoginId(loginId);

        if (memberOpt.isPresent()) {
            Member member = memberOpt.get();

            // 🚨 1. 계정이 잠겨있는지 먼저 확인
            if (member.getLockTime() != null) {
                // 아직 10분이 안 지났다면 로그인 거부
                if (LocalDateTime.now().isBefore(member.getLockTime().plusMinutes(10))) {
                    return ResponseEntity.status(403).body("비밀번호 5회 오류로 계정이 잠겼습니다. 잠시 후 다시 시도해주세요.");
                } else {
                    // 10분이 지났다면 잠금 해제 및 횟수 초기화
                    member.setLockTime(null);
                    member.setFailedAttempts(0);
                }
            }

            // ✅ 2. 비밀번호가 맞았을 때
            if (member.getPassword().equals(password)) {
                member.setFailedAttempts(0); // 성공했으니 틀린 횟수 0으로 리셋
                member.setLockTime(null);
                memberRepository.save(member);
                return ResponseEntity.ok("로그인 성공");
            } 
            // ❌ 3. 비밀번호가 틀렸을 때
            else {
                int attempts = member.getFailedAttempts() + 1;
                member.setFailedAttempts(attempts); // 틀린 횟수 +1

                if (attempts >= 5) {
                    member.setLockTime(LocalDateTime.now()); // 현재 시간으로 잠금 설정
                    memberRepository.save(member);
                    return ResponseEntity.status(403).body("비밀번호를 5회 틀려 10분 동안 계정이 잠깁니다.");
                }

                memberRepository.save(member);
                return ResponseEntity.status(401).body("비밀번호가 틀렸습니다. (남은 횟수: " + (5 - attempts) + ")");
            }
        }

        return ResponseEntity.status(401).body("존재하지 않는 아이디입니다.");
        
        
    }
 // 🔍 3. 아이디 중복 확인 API (/api/members/check-id)
    @GetMapping("/check-id")
    public ResponseEntity<?> checkDuplicateId(@RequestParam String loginId) {
        // DB에 해당 아이디가 존재하는지 확인
        if (memberRepository.findByLoginId(loginId).isPresent()) {
            // 이미 존재하면 409 Conflict 에러 반환 (프론트엔드의 response.ok가 false가 됨)
            return ResponseEntity.status(409).body("이미 사용 중인 아이디입니다.");
        }
        
        // 존재하지 않으면 200 OK 성공 반환 (프론트엔드의 response.ok가 true가 됨)
        return ResponseEntity.ok("사용 가능한 아이디입니다.");
    }
}
