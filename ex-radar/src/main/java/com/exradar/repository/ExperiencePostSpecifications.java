package com.exradar.repository;

import com.exradar.dto.ExperienceSearchCriteria;
import com.exradar.entity.ExperiencePost;
import com.exradar.entity.PostStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public final class ExperiencePostSpecifications {
  private ExperiencePostSpecifications() {}

  public static Specification<ExperiencePost> publicSearch(ExperienceSearchCriteria c) {
    return (root, q, cb) -> {
      q.distinct(true);
      Predicate p = cb.equal(root.get("status"), PostStatus.PUBLISHED);
      if (text(c.keyword())) {
        String k = "%" + c.keyword().trim().toLowerCase() + "%";
        p =
            cb.and(
                p,
                cb.or(
                    cb.like(cb.lower(root.get("title")), k),
                    cb.like(cb.lower(root.get("situationBefore")), k),
                    cb.like(cb.lower(root.get("worries")), k),
                    cb.like(cb.lower(root.get("choiceMade")), k),
                    cb.like(cb.lower(root.get("outcome")), k)));
      }
      if (c.categoryId() != null)
        p = cb.and(p, cb.equal(root.get("category").get("id"), c.categoryId()));
      if (text(c.tag()))
        p =
            cb.and(
                p, cb.equal(cb.lower(root.join("tags").get("name")), c.tag().trim().toLowerCase()));
      if (c.ageFrom() != null) p = cb.and(p, cb.ge(root.get("ageAtChoice"), c.ageFrom()));
      if (c.ageTo() != null) p = cb.and(p, cb.le(root.get("ageAtChoice"), c.ageTo()));
      if (text(c.currentAgeGroup()))
        p = cb.and(p, cb.equal(root.get("currentAgeGroup"), c.currentAgeGroup()));
      if (text(c.education()))
        p =
            cb.and(
                p,
                cb.like(
                    cb.lower(root.get("author").get("education")),
                    "%" + c.education().trim().toLowerCase() + "%"));
      if (text(c.occupation()))
        p =
            cb.and(
                p,
                cb.like(
                    cb.lower(root.get("author").get("occupation")),
                    "%" + c.occupation().trim().toLowerCase() + "%"));
      if (c.satisfactionMin() != null)
        p = cb.and(p, cb.ge(root.get("satisfaction"), c.satisfactionMin()));
      if (c.regretMax() != null) p = cb.and(p, cb.le(root.get("regret"), c.regretMax()));
      if (c.yearsMin() != null) p = cb.and(p, cb.ge(root.get("yearsElapsed"), c.yearsMin()));
      if (c.yearsMax() != null) p = cb.and(p, cb.le(root.get("yearsElapsed"), c.yearsMax()));
      if (c.chooseAgain() != null)
        p = cb.and(p, cb.equal(root.get("chooseAgain"), c.chooseAgain()));
      if (c.dateFrom() != null)
        p = cb.and(p, cb.greaterThanOrEqualTo(root.get("createdAt"), c.dateFrom().atStartOfDay()));
      if (c.dateTo() != null)
        p =
            cb.and(
                p,
                cb.lessThan(root.get("createdAt"), c.dateTo().plusDays(1).atStartOfDay()));
      return p;
    };
  }

  /**
   * 「教訓まとめ」ページ用。公開投稿のうち、教訓(learned・lessonのいずれか)が実際に
   * 記入されているものだけを対象にする(教訓を読むためのページに「まだ記載されていません」
   * という空のカードを並べても閲覧体験を損なうだけのため)。
   * keywordは教訓本文とタグ名を横断的に検索し(4-1)、tagは既存のタグ絞り込みと同じ完全一致
   * 条件(4-2、既存のExperienceSearchCriteria.tagをそのまま再利用)で個別に絞り込める。
   */
  public static Specification<ExperiencePost> publicLessonSearch(ExperienceSearchCriteria c) {
    return (root, q, cb) -> {
      q.distinct(true);
      Predicate p = cb.equal(root.get("status"), PostStatus.PUBLISHED);
      p =
          cb.and(
              p,
              cb.or(
                  cb.gt(cb.length(cb.trim(cb.coalesce(root.get("learned"), ""))), 0),
                  cb.gt(cb.length(cb.trim(cb.coalesce(root.get("lesson"), ""))), 0)));
      if (c.categoryId() != null)
        p = cb.and(p, cb.equal(root.get("category").get("id"), c.categoryId()));
      if (text(c.keyword())) {
        String k = "%" + c.keyword().trim().toLowerCase() + "%";
        var keywordTagJoin = root.join("tags", JoinType.LEFT);
        p =
            cb.and(
                p,
                cb.or(
                    cb.like(cb.lower(cb.coalesce(root.get("learned"), "")), k),
                    cb.like(cb.lower(cb.coalesce(root.get("lesson"), "")), k),
                    cb.like(cb.lower(keywordTagJoin.get("name")), k)));
      }
      if (text(c.tag()))
        p =
            cb.and(
                p, cb.equal(cb.lower(root.join("tags").get("name")), c.tag().trim().toLowerCase()));
      return p;
    };
  }

  private static boolean text(String s) {
    return s != null && !s.isBlank();
  }
}
