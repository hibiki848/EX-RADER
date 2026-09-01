package com.exradar.dto;

import com.exradar.entity.ReactionType;
import java.util.*;

public record ReactionSummary(Map<ReactionType, Long> counts, Set<ReactionType> mine) {
  public long count(ReactionType type) {
    return counts.getOrDefault(type, 0L);
  }

  public boolean selected(ReactionType type) {
    return mine.contains(type);
  }
}
