package com.exradar.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.exradar.entity.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {
	void deleteByReporterId(Long reporterId);
}
