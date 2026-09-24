package com.example.startup.repository;

import com.example.startup.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


//JpaRepository<어떤 엔티티를 다룰지, 그 엔티티의 PK(ID) 타입>
public interface ReportRepository extends JpaRepository<Report, Long> {
	List<Report> findAllByLoginIdOrderByCreatedAtDesc(String loginId);
}
