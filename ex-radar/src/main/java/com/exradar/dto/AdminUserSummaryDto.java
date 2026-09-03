package com.exradar.dto;

import com.exradar.entity.PlanType;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 管理者ユーザー一覧の1行分の表示用データ。 */
public record AdminUserSummaryDto(
    Long id,
    String displayName,
    String email,
    Role role,
    boolean suspended,
    boolean analyticsExcluded,
    PlanType currentPlan,
    LocalDateTime registeredAt,
    LocalDateTime firstLoginAt,
    LocalDateTime lastLoginAt,
    LocalDateTime firstPostAt,
    LocalDateTime lastPostAt,
    long postCount,
    LocalDateTime firstPaidAt,
    Long paidDurationDays) {

  public static AdminUserSummaryDto from(
      User u, long postCount, LocalDateTime firstPostAt, LocalDateTime lastPostAt) {
    return new AdminUserSummaryDto(
        u.getId(),
        u.getDisplayName(),
        u.getEmail(),
        u.getRole(),
        u.isSuspended(),
        u.isAnalyticsExcluded(),
        u.getCurrentPlan(),
        u.getCreatedAt(),
        u.getFirstLoginAt(),
        u.getLastLoginAt(),
        firstPostAt,
        lastPostAt,
        postCount,
        u.getFirstPaidAt(),
        paidDurationDays(u));
  }

  private static Long paidDurationDays(User u) {
    if (u.getPremiumPeriodStartedAt() == null) return null;
    LocalDateTime end = u.getPremiumPeriodEndedAt() != null ? u.getPremiumPeriodEndedAt() : LocalDateTime.now();
    return ChronoUnit.DAYS.between(u.getPremiumPeriodStartedAt(), end);
  }
}
