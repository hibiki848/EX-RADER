package com.exradar.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 一覧の「もっと見る」JSON APIのレスポンス形式。Spring DataのPageをそのままJSON化せず、
 * フロント(experience-list.js)が必要とする最小限の形に絞って明示的なDTOにする。
 */
public record ExperienceListPageDto(
    List<ExperienceCardDto> content, int number, int totalPages, long totalElements, boolean hasNext) {
  public static ExperienceListPageDto from(Page<ExperienceCardDto> page) {
    return new ExperienceListPageDto(
        page.getContent(), page.getNumber(), page.getTotalPages(), page.getTotalElements(), page.hasNext());
  }
}
