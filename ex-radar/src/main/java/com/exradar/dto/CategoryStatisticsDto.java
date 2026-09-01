package com.exradar.dto;

import java.util.List;

public record CategoryStatisticsDto(
    Long categoryId,
    String categoryName,
    long postCount,
    long contributorCount,
    double averageSatisfaction,
    double averageRegret,
    double chooseAgainPercentage,
    List<LabelCount> commonRegrets,
    List<LabelCount> nextRoutes,
    List<LabelCount> currentOccupations) {
  public record LabelCount(String label, long count) {}

  public boolean hasData() {
    return postCount > 0;
  }
}
