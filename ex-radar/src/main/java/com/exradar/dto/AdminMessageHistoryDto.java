package com.exradar.dto;

import java.time.LocalDateTime;

/** 管理者向けの配信履歴一覧の1行分。 */
public record AdminMessageHistoryDto(
    Long id,
    String title,
    String createdByAdminDisplayName,
    LocalDateTime sentAt,
    long deliveredCount,
    long readCount,
    long unreadCount) {}
