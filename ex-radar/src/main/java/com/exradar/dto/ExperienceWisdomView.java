package com.exradar.dto;

import com.exradar.entity.ExperiencePost;
import com.exradar.entity.PersonalValue;
import java.util.Set;

/**
 * 体験談の「学び」部分(振り返り)のみを保持するビュー。自分自身の公開体験談を
 * 1件以上投稿しているユーザーにのみControllerが生成・Modelへ渡す。未解放の閲覧者に
 * 対してはこのオブジェクト自体をModelへ入れない(nullのまま)ことで、テンプレート側の
 * 実装ミスに関わらず学び本文がHTMLへ出力されない構成にする。
 */
public record ExperienceWisdomView(
    String decisionCriteria,
    String learned,
    String wishKnown,
    String unexpectedlyOkay,
    String preparationHelped,
    String missedRegret,
    String lesson,
    String suitableFor,
    String cautionFor,
    String adviceToPastSelf,
    Set<PersonalValue> values) {
  public static ExperienceWisdomView from(ExperiencePost p) {
    return new ExperienceWisdomView(
        p.getDecisionCriteria(),
        p.getLearned(),
        p.getWishKnown(),
        p.getUnexpectedlyOkay(),
        p.getPreparationHelped(),
        p.getMissedRegret(),
        p.getLesson(),
        p.getSuitableFor(),
        p.getCautionFor(),
        p.getAdviceToPastSelf(),
        p.getValues());
  }
}
