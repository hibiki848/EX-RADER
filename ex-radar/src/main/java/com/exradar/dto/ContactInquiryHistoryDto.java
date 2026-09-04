package com.exradar.dto;

import com.exradar.entity.ContactCategory;
import com.exradar.entity.InquiryStatus;
import java.time.LocalDateTime;

/** 管理者向けのお問い合わせ一覧の1行分。 */
public record ContactInquiryHistoryDto(
    Long id,
    ContactCategory category,
    String subject,
    String userDisplayName,
    String email,
    InquiryStatus status,
    LocalDateTime createdAt) {}
