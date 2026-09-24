package com.example.startup.controller;

import com.example.startup.entity.Member;
import com.example.startup.repository.MemberRepository;
import com.example.startup.service.AuthTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{4,20}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*[!@#$%^&*()_+={}\\[\\]:;\"'<>,.?/\\\\|`~]).{8,20}$");

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;

    public MemberController(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> data) {
        String loginId = normalizeLoginId(data.get("loginId"));
        String password = data.get("password");

        if (loginId == null || !LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            return ResponseEntity.badRequest().body("아이디는 영문, 숫자, 밑줄, 하이픈을 사용해 4~20자로 입력해주세요.");
        }
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            return ResponseEntity.badRequest().body("비밀번호는 영문자와 특수문자를 포함해 8~20자로 입력해주세요.");
        }
        if (memberRepository.findByLoginId(loginId).isPresent()) {
            return ResponseEntity.status(409).body("이미 존재하는 아이디입니다.");
        }

        Member newMember = new Member();
        newMember.setLoginId(loginId);
        newMember.setPassword(passwordEncoder.encode(password));
        memberRepository.save(newMember);
        return ResponseEntity.ok("회원가입 성공!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> data) {
        String loginId = normalizeLoginId(data.get("loginId"));
        String password = data.get("password");

        if (loginId == null || password == null) {
            return ResponseEntity.status(401).body("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        Optional<Member> memberOpt = memberRepository.findByLoginId(loginId);
        if (memberOpt.isEmpty()) {
            return ResponseEntity.status(401).body("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        Member member = memberOpt.get();
        if (member.getLockTime() != null) {
            if (LocalDateTime.now().isBefore(member.getLockTime().plusMinutes(10))) {
                return ResponseEntity.status(403).body("로그인 시도가 여러 번 실패해 계정이 잠겼습니다. 잠시 후 다시 시도해주세요.");
            }
            member.setLockTime(null);
            member.setFailedAttempts(0);
        }

        boolean isHashed = isBcryptHash(member.getPassword());
        boolean passwordMatches = isHashed
                ? passwordEncoder.matches(password, member.getPassword())
                : constantTimeEquals(member.getPassword(), password);

        if (!passwordMatches) {
            int attempts = member.getFailedAttempts() + 1;
            member.setFailedAttempts(attempts);
            if (attempts >= 5) {
                member.setLockTime(LocalDateTime.now());
            }
            memberRepository.save(member);
            if (attempts >= 5) {
                return ResponseEntity.status(403).body("로그인 시도가 여러 번 실패해 계정이 잠겼습니다. 잠시 후 다시 시도해주세요.");
            }
            return ResponseEntity.status(401).body("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        if (!isHashed) {
            member.setPassword(passwordEncoder.encode(password));
        }
        member.setFailedAttempts(0);
        member.setLockTime(null);
        memberRepository.save(member);

        return ResponseEntity.ok(Map.of(
                "message", "로그인 성공",
                "token", authTokenService.issueToken(member.getLoginId()),
                "loginId", member.getLoginId(),
                "points", member.getPoints()));
    }

    @GetMapping("/check-id")
    public ResponseEntity<?> checkDuplicateId(@RequestParam String loginId) {
        String normalizedLoginId = normalizeLoginId(loginId);
        if (normalizedLoginId == null || !LOGIN_ID_PATTERN.matcher(normalizedLoginId).matches()) {
            return ResponseEntity.badRequest().body("아이디 형식이 올바르지 않습니다.");
        }
        if (memberRepository.findByLoginId(normalizedLoginId).isPresent()) {
            return ResponseEntity.status(409).body("이미 사용 중인 아이디입니다.");
        }
        return ResponseEntity.ok("사용 가능한 아이디입니다.");
    }

    private String normalizeLoginId(String loginId) {
        return loginId == null ? null : loginId.trim();
    }

    private boolean isBcryptHash(String password) {
        return password != null && (password.startsWith("$2a$")
                || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        int difference = 0;
        for (int index = 0; index < left.length(); index++) {
            difference |= left.charAt(index) ^ right.charAt(index);
        }
        return difference == 0;
    }
}
