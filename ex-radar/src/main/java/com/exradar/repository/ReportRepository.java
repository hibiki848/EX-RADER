package com.exradar.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.exradar.entity.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {
  void deleteByReporterId(Long reporterId);

  @EntityGraph(attributePaths = "reporter")
  List<Report> findAllByOrderByCreatedAtDesc();
}
