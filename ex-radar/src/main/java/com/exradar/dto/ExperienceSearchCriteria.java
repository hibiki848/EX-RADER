package com.exradar.dto;

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
    Boolean chooseAgain) {
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
        && chooseAgain == null;
  }

  private static boolean blank(String v) {
    return v == null || v.isBlank();
  }
}
