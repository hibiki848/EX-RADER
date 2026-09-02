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
  private ReportStatus status = ReportStatus.PENDING;

  protected Report() {}

  public Report(User reporter, ReportTargetType type, Long targetId, String reason) {
    this.reporter = reporter;
    this.targetType = type;
    this.targetId = targetId;
    this.reason = reason;
  }

  public User getReporter() {
    return reporter;
  }

  public ReportTargetType getTargetType() {
    return targetType;
  }

  public Long getTargetId() {
    return targetId;
  }

  public String getReason() {
    return reason;
  }

  public ReportStatus getStatus() {
    return status;
  }

  public void changeStatus(ReportStatus status) {
    this.status = status;
  }
}
