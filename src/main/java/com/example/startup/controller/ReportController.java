package com.example.startup.controller;

import com.example.startup.entity.Member;
import com.example.startup.entity.Report;
import com.example.startup.repository.MemberRepository;
import com.example.startup.repository.ReportRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;

import java.util.List;
import java.util.Optional;
import java.util.Map;


@RestController
@CrossOrigin(origins = {
	    "https://startup-frontend-rho.vercel.app",
	    "http://localhost:3000"
	})
public class ReportController {

    private final ReportRepository reportRepository;

    private final S3Service s3Service;
    
    public ReportController(ReportRepository reportRepository, S3Service s3Service) {
        this.reportRepository = reportRepository;
        this.s3Service = s3Service;
    }
    
    // 🌟 프론트엔드에서 데이터를 달라고(GET) 할 때 실행되는 부분
    @GetMapping("/api/reports")
    public List<Report> getAllReports() {
        // DB에 저장된 모든 신고 내역을 찾아서 프론트엔드로 보내줌
        return reportRepository.findAll();
    }
    @Autowired
    private MemberRepository memberRepository;
    
    @PostMapping("/api/reports")
    public ResponseEntity<?> createReport( // 🌟 에러 코드를 주기 위해 ResponseEntity로 변경
            @RequestParam("latitude") Double latitude,
            @RequestParam("longitude") Double longitude,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("loginId") String loginId) throws IOException { // 🌟 required=false 삭제 (무조건 받아야 함)
        
        // 🚨 1. DB에 진짜 있는 회원인지 검사 (비회원 차단)
        Optional<Member> memberOpt = memberRepository.findByLoginId(loginId);
        if (memberOpt.isEmpty()) {
            return ResponseEntity.status(401).body("로그인 정보가 유효하지 않습니다. 다시 로그인해주세요.");
        }

        // 2. 사진 폴더 설정 및 저장
        String imageUrl = s3Service.uploadFile(image);

        // 3. DB에 신고 내역 저장
        Report report = new Report();
        report.setLatitude(latitude);
        report.setLongitude(longitude);
        report.setImageUrl(imageUrl);
        report.setDescription(description);
        report.setLoginId(loginId);
        reportRepository.save(report);
        

        // 4. 포인트 적립 (회원이 확실하므로 바로 적립!)
        Member member = memberOpt.get();
        member.setPoints(member.getPoints() + 100); 
        memberRepository.save(member);

        return ResponseEntity.ok("신고가 접수되었습니다! 100포인트가 적립되었습니다. 🪙");
    }
    
 // ==========================================
    // ✏️ 1. 신고 내역 수정 (description만 변경)
    // ==========================================
    @PutMapping("/api/reports/{id}")
    public ResponseEntity<?> updateReport(
            @PathVariable("id") Long id, 
            @RequestBody Map<String, String> requestBody) {
        
        // 1. DB에서 해당 신고 내역이 있는지 확인
        Optional<Report> reportOpt = reportRepository.findById(id);
        if (reportOpt.isEmpty()) {
            return ResponseEntity.status(404).body("해당 신고 내역을 찾을 수 없습니다.");
        }

        // 2. 내용(description) 수정 및 저장
        Report report = reportOpt.get();
        String newDescription = requestBody.get("description");
        
        // 프론트엔드에서 100자로 제한했지만 백엔드에서도 한번 더 방어 (보안)
        if (newDescription != null && newDescription.length() > 100) {
            newDescription = newDescription.substring(0, 100);
        }
        
        report.setDescription(newDescription);
        reportRepository.save(report);

        return ResponseEntity.ok("신고 내역이 성공적으로 수정되었습니다.");
    }

    // ==========================================
    // 🗑️ 2. 신고 내역 삭제 (DB + 실제 사진 파일 동시 삭제)
    // ==========================================
    @DeleteMapping("/api/reports/{id}")
    public ResponseEntity<?> deleteReport(@PathVariable("id") Long id) {
        
        // 1. DB에서 해당 신고 내역이 있는지 확인
        Optional<Report> reportOpt = reportRepository.findById(id);
        if (reportOpt.isEmpty()) {
            return ResponseEntity.status(404).body("해당 신고 내역을 찾을 수 없습니다.");
        }

        Report report = reportOpt.get();

        String imageUrl = report.getImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            s3Service.deleteFile(imageUrl);
        }

        // 3. DB에서 신고 내역 삭제
        reportRepository.delete(report);

        return ResponseEntity.ok("신고 내역과 사진이 완전히 삭제되었습니다.");
    }
    
}