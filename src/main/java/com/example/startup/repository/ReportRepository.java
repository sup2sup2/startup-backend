package com.example.startup.repository;

import com.example.startup.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;


//JpaRepository<어떤 엔티티를 다룰지, 그 엔티티의 PK(ID) 타입>
public interface ReportRepository extends JpaRepository<Report, Long> {
	
}
