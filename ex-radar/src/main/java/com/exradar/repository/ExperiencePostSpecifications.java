package com.exradar.repository;

import com.exradar.dto.ExperienceSearchCriteria;
import com.exradar.entity.ExperiencePost;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public final class ExperiencePostSpecifications {
  private ExperiencePostSpecifications() {}

  public static Specification<ExperiencePost> publicSearch(ExperienceSearchCriteria c) {
    return (root, q, cb) -> {
      q.distinct(true);
      Predicate p = cb.isTrue(root.get("published"));
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
      return p;
    };
  }

  private static boolean text(String s) {
    return s != null && !s.isBlank();
  }
}
