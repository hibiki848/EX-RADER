package com.exradar.dto;

import com.exradar.entity.Report;
import com.exradar.entity.ReportStatus;
import com.exradar.entity.ReportTargetType;
import java.time.LocalDateTime;

/** 管理画面の通報一覧向け。通報対象(体験談/コメント)は外部キーではなくID参照のため、表示に必要な情報をここへ集約する。 */
public record AdminReportDto(
    Long id,
    LocalDateTime createdAt,
    ReportTargetType targetType,
    Long targetId,
    String reason,
    ReportStatus status,
    String reporterDisplayName,
    String reporterEmail,
    boolean targetExists,
    String targetSummary,
    String targetAuthorDisplayName,
    Long targetPostId) {
  public static AdminReportDto pending(Report r, TargetInfo target) {
    return new AdminReportDto(
        r.getId(),
        r.getCreatedAt(),
        r.getTargetType(),
        r.getTargetId(),
        r.getReason(),
        r.getStatus(),
        r.getReporter().getDisplayName(),
        r.getReporter().getEmail(),
        target.exists(),
        target.summary(),
        target.authorDisplayName(),
        target.postId());
  }

  public record TargetInfo(boolean exists, String summary, String authorDisplayName, Long postId) {
    public static TargetInfo missing() {
      return new TargetInfo(false, "(削除済み、または見つかりません)", null, null);
    }
  }
}
