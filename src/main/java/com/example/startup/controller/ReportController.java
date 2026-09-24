package com.example.startup.controller;

import com.example.startup.entity.Member;
import com.example.startup.entity.Report;
import com.example.startup.repository.MemberRepository;
import com.example.startup.repository.ReportRepository;
import com.example.startup.service.AuthTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
public class ReportController {

    private static final int MAX_DESCRIPTION_LENGTH = 100;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");

    private final ReportRepository reportRepository;
    private final MemberRepository memberRepository;
    private final S3Service s3Service;
    private final AuthTokenService authTokenService;

    public ReportController(ReportRepository reportRepository, MemberRepository memberRepository,
            S3Service s3Service, AuthTokenService authTokenService) {
        this.reportRepository = reportRepository;
        this.memberRepository = memberRepository;
        this.s3Service = s3Service;
        this.authTokenService = authTokenService;
    }

    @GetMapping("/api/reports")
    public List<ReportResponse> getAllReports() {
        return reportRepository.findAll().stream().map(ReportResponse::from).toList();
    }

    @GetMapping("/api/reports/mine")
    public List<ReportResponse> getMyReports(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String loginId = authTokenService.requireLoginId(authorization);
        return reportRepository.findAllByLoginIdOrderByCreatedAtDesc(loginId).stream()
                .map(ReportResponse::from)
                .toList();
    }

    @PostMapping("/api/reports")
    @Transactional
    public ResponseEntity<?> createReport(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("latitude") Double latitude,
            @RequestParam("longitude") Double longitude,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "description", required = false) String description) throws IOException {
        String loginId = authTokenService.requireLoginId(authorization);
        Optional<Member> memberOpt = memberRepository.findByLoginId(loginId);
        if (memberOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("회원 정보를 찾을 수 없습니다.");
        }
        if (!isValidCoordinate(latitude, longitude)) {
            return ResponseEntity.badRequest().body("위치 정보가 올바르지 않습니다.");
        }
        String contentType = image.getContentType() == null
                ? "" : image.getContentType().toLowerCase(Locale.ROOT);
        if (image.isEmpty() || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body("JPEG, PNG, WebP 또는 HEIC 이미지만 업로드할 수 있습니다.");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            return ResponseEntity.badRequest().body("신고 내용은 100자까지 입력할 수 있습니다.");
        }

        String imageUrl = s3Service.uploadFile(image);
        Report report = new Report();
        report.setLatitude(latitude);
        report.setLongitude(longitude);
        report.setImageUrl(imageUrl);
        report.setDescription(description == null ? "" : description);
        report.setLoginId(loginId);
        reportRepository.save(report);

        Member member = memberOpt.get();
        member.setPoints(member.getPoints() + 100);
        memberRepository.save(member);

        return ResponseEntity.ok(Map.of(
                "message", "신고가 접수되었습니다! 100포인트가 적립되었습니다.",
                "points", member.getPoints()));
    }

    @PutMapping("/api/reports/{id}")
    public ResponseEntity<?> updateReport(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id, @RequestBody Map<String, String> requestBody) {
        String loginId = authTokenService.requireLoginId(authorization);
        Optional<Report> reportOpt = reportRepository.findById(id);
        if (reportOpt.isEmpty()) {
            return ResponseEntity.status(404).body("해당 신고 내역을 찾을 수 없습니다.");
        }

        Report report = reportOpt.get();
        if (!loginId.equals(report.getLoginId())) {
            return ResponseEntity.status(403).body("본인이 작성한 신고만 수정할 수 있습니다.");
        }

        String newDescription = requestBody.get("description");
        if (newDescription == null || newDescription.length() > MAX_DESCRIPTION_LENGTH) {
            return ResponseEntity.badRequest().body("신고 내용은 100자까지 입력할 수 있습니다.");
        }
        report.setDescription(newDescription);
        reportRepository.save(report);
        return ResponseEntity.ok("신고 내역이 성공적으로 수정되었습니다.");
    }

    @DeleteMapping("/api/reports/{id}")
    public ResponseEntity<?> deleteReport(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        String loginId = authTokenService.requireLoginId(authorization);
        Optional<Report> reportOpt = reportRepository.findById(id);
        if (reportOpt.isEmpty()) {
            return ResponseEntity.status(404).body("해당 신고 내역을 찾을 수 없습니다.");
        }

        Report report = reportOpt.get();
        if (!loginId.equals(report.getLoginId())) {
            return ResponseEntity.status(403).body("본인이 작성한 신고만 삭제할 수 있습니다.");
        }

        String imageUrl = report.getImageUrl();
        if (imageUrl != null && !imageUrl.isBlank()) {
            s3Service.deleteFile(imageUrl);
        }
        reportRepository.delete(report);
        return ResponseEntity.ok("신고 내역과 사진이 삭제되었습니다.");
    }

    private boolean isValidCoordinate(Double latitude, Double longitude) {
        return latitude != null && longitude != null
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }

    public record ReportResponse(Long id, Double latitude, Double longitude,
            String imageUrl, String description) {
        private static ReportResponse from(Report report) {
            return new ReportResponse(report.getId(), report.getLatitude(), report.getLongitude(),
                    report.getImageUrl(), report.getDescription());
        }
    }
}
