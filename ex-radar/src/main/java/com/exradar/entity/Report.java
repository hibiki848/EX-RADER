package com.exradar.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "reports")
public class Report extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User reporter;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReportTargetType targetType;

  @Column(nullable = false)
  private Long targetId;

  @Column(nullable = false, length = 1000)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReportStatus status = ReportStatus.OPEN;

  protected Report() {}

  public Report(User reporter, ReportTargetType type, Long targetId, String reason) {
    this.reporter = reporter;
    this.targetType = type;
    this.targetId = targetId;
    this.reason = reason;
  }
}
