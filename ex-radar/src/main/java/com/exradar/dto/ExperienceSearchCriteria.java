package com.exradar.dto;

import java.time.LocalDate;

public record ExperienceSearchCriteria(
    String keyword,
    Long categoryId,
    String tag,
    Integer ageFrom,
    Integer ageTo,
    String currentAgeGroup,
    String education,
    String occupation,
    Integer satisfactionMin,
    Integer regretMax,
    Integer yearsMin,
    Integer yearsMax,
    Boolean chooseAgain,
    LocalDate dateFrom,
    LocalDate dateTo) {
  public boolean empty() {
    return blank(keyword)
        && categoryId == null
        && blank(tag)
        && ageFrom == null
        && ageTo == null
        && blank(currentAgeGroup)
        && blank(education)
        && blank(occupation)
        && satisfactionMin == null
        && regretMax == null
        && yearsMin == null
        && yearsMax == null
        && chooseAgain == null
        && dateFrom == null
        && dateTo == null;
  }

  /** カテゴリのみで絞り込んでいる状態か(SEO上「カテゴリページ」として自己canonicalにしてよいか判定用)。 */
  public boolean categoryOnly() {
    return categoryId != null
        && blank(keyword)
        && blank(tag)
        && ageFrom == null
        && ageTo == null
        && blank(currentAgeGroup)
        && blank(education)
        && blank(occupation)
        && satisfactionMin == null
        && regretMax == null
        && yearsMin == null
        && yearsMax == null
        && chooseAgain == null
        && dateFrom == null
        && dateTo == null;
  }

  /** 絞り込みバーの見出しに「〇件指定中」と表示するための、有効な検索条件の数。 */
  public int activeConditionCount() {
    int count = 0;
    if (!blank(keyword)) count++;
    if (categoryId != null) count++;
    if (!blank(tag)) count++;
    if (ageFrom != null) count++;
    if (ageTo != null) count++;
    if (!blank(currentAgeGroup)) count++;
    if (!blank(education)) count++;
    if (!blank(occupation)) count++;
    if (satisfactionMin != null) count++;
    if (regretMax != null) count++;
    if (yearsMin != null) count++;
    if (yearsMax != null) count++;
    if (chooseAgain != null) count++;
    if (dateFrom != null) count++;
    if (dateTo != null) count++;
    return count;
  }

  private static boolean blank(String v) {
    return v == null || v.isBlank();
  }
}
