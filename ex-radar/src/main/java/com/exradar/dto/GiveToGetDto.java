package com.exradar.dto;

import java.util.List;

public record GiveToGetDto(
    int ownPostCount,
    int unlockedLevel,
    List<String> unlocked,
    List<String> locked,
    CategoryStatisticsDto basicStatistics,
    List<SimilarityDto> similarPosts,
    List<SimilarityDto> similarUsers) {
  public boolean canSeeSimilarUsers() {
    return unlockedLevel >= 2;
  }

  public boolean canSeeBasicStatistics() {
    return unlockedLevel >= 1;
  }

  public boolean canSeeDetailedStatistics() {
    return unlockedLevel >= 3;
  }

  public boolean canSeeNextRoutes() {
    return unlockedLevel >= 4;
  }

  public boolean canSeeSatisfactionTrends() {
    return unlockedLevel >= 5;
  }
}
