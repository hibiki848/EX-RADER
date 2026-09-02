package com.exradar.dto;

import com.exradar.entity.ExperiencePost;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一覧・トップページ等でカード表示に使う「経験・失敗」中心のDTO。
 * learned/lessonは「学び」に属するため、呼び出し側が渡すwisdomUnlocked(=
 * ExperiencePostService#canReadWisdom/canReadExperiencesの結果)がfalseの場合は
 * nullのまま生成する。未解放の閲覧者にはこのオブジェクト自体に学び本文が
 * 一切含まれない構成にすることで、テンプレート側の実装ミスに関わらず
 * HTMLソース・data属性・JSON等のどこにも学び本文が出力されないようにする。
 */
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
    String difficulties,
    String learned,
    String lesson,
    List<String> tags,
    LocalDateTime createdAt) {
  public static ExperienceCardDto from(ExperiencePost p, boolean wisdomUnlocked) {
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
        p.getDifficulties(),
        wisdomUnlocked ? p.getLearned() : null,
        wisdomUnlocked ? p.getLesson() : null,
        p.getTags().stream().map(t -> t.getName()).sorted().toList(),
        p.getCreatedAt());
  }
}
