package com.exradar.dto;

import com.exradar.entity.ExperiencePost;
import java.time.LocalDateTime;
import java.util.List;

public record ExperienceCardDto(
    Long id,
    String title,
    String category,
    String author,
    Integer ageAtChoice,
    String currentAgeGroup,
    Integer yearsElapsed,
    int satisfaction,
    int regret,
    boolean chooseAgain,
    String situationBefore,
    String choiceMade,
    String outcome,
    String learned,
    String lesson,
    List<String> tags,
    LocalDateTime createdAt) {
  public static ExperienceCardDto from(ExperiencePost p) {
    return new ExperienceCardDto(
        p.getId(),
        p.getTitle(),
        p.getCategory().getName(),
        p.getAuthor().getDisplayName(),
        p.getAgeAtChoice(),
        p.getCurrentAgeGroup(),
        p.getYearsElapsed(),
        p.getSatisfaction(),
        p.getRegret(),
        p.isChooseAgain(),
        p.getSituationBefore(),
        p.getChoiceMade(),
        p.getOutcome(),
        p.getLearned(),
        p.getLesson(),
        p.getTags().stream().map(t -> t.getName()).sorted().toList(),
        p.getCreatedAt());
  }
}
