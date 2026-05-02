package com.example.startup.controller;

import com.example.startup.entity.Report;
import com.example.startup.repository.ReportRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class ReportController {

    private final ReportRepository reportRepository;

    public ReportController(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }
    
    // 🌟 프론트엔드에서 데이터를 달라고(GET) 할 때 실행되는 부분
    @GetMapping("/api/reports")
    public List<Report> getAllReports() {
        // DB에 저장된 모든 신고 내역을 찾아서 프론트엔드로 보내줌
        return reportRepository.findAll();
    }

    @PostMapping("/api/reports")
    public String createReport(
            @RequestParam("latitude") Double latitude,
            @RequestParam("longitude") Double longitude,
            @RequestParam("image") MultipartFile image) throws IOException {

        // 1. 사진을 저장할 폴더 위치 설정 (본인 컴퓨터 경로에 맞게 수정 가능)
        String uploadDir = System.getProperty("user.dir") + "/uploads/";
        File folder = new File(uploadDir);
        if (!folder.exists()) folder.mkdirs(); // 폴더 없으면 만들기

        // 2. 파일 이름이 겹치지 않게 고유한 이름 생성 (UUID)
        String fileName = UUID.randomUUID().toString() + "_" + image.getOriginalFilename();
        File saveFile = new File(uploadDir + fileName);

        // 3. 실제 폴더에 파일 저장
        image.transferTo(saveFile);

        // 4. DB에는 위치 정보와 저장된 파일의 주소를 저장
        Report report = new Report();
        report.setLatitude(latitude);
        report.setLongitude(longitude);
        report.setImageUrl("/uploads/" + fileName); // 나중에 불러올 경로

        reportRepository.save(report);

        return "사진과 함께 신고가 접수되었습니다!";
    }
}