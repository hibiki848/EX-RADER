package com.exradar.dto;

import java.util.List;

public record LifeReportDto(
    long postCount,
    double averageSatisfaction,
    double averageRegret,
    double chooseAgainPercentage,
    List<CategoryStatisticsDto.LabelCount> categories,
    List<RouteItem> route,
    long referencedPostCount,
    long similarPostCount,
    long similarUserCount) {
  public record RouteItem(String choice, String category, String event, String timing) {}
}
