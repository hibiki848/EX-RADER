package com.exradar.dto;

import java.time.LocalDateTime;

/** 管理者向けの「ログイン時お知らせ」一覧の1行分。 */
public record AdminAnnouncementHistoryDto(
    Long id,
    String title,
    boolean published,
    String createdByAdminDisplayName,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    long deliveredCount,
    long displayedCount,
    long dismissedPermanentlyCount) {}
