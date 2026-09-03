package com.exradar.repository;

import com.exradar.dto.AdminUserSearchCriteria;
import com.exradar.entity.ExperiencePost;
import com.exradar.entity.PostStatus;
import com.exradar.entity.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.sqm.TemporalUnit;
import org.springframework.data.jpa.domain.Specification;

/**
 * 管理者のユーザー検索条件(AdminUserSearchCriteria)を、DB側でWHERE句として評価される
 * Specification&lt;User&gt;へ変換する。個別・一括メッセージ配信、ログイン時のお知らせ配信
 * からもこのクラスをそのまま再利用することを想定している(ユーザー一覧専用のロジックとして
 * Controller/Serviceへ埋め込まない)。
 *
 * 投稿数・初回投稿日・有料加入期間はUser側に非正規化して持たせず、常にExperiencePost/User
 * 自身のカラムに対する相関サブクエリ・関数式として都度計算する(投稿の追加・削除時に
 * 非正規化カラムを同期し続ける必要がなく、整合性が崩れる心配がない)。
 * H2(MODE=PostgreSQL)・PostgreSQLの両方で同じSQLを生成できるよう、日付の差分計算は
 * JPA標準のcb.diff(Number,Number)ではなくHibernateCriteriaBuilder#durationBetween/
 * durationByUnitを使う(Hibernateがダイアレクトごとに適切なSQLへ翻訳する)。
 */
public final class AdminUserSpecifications {
  private AdminUserSpecifications() {}

  public static Specification<User> build(AdminUserSearchCriteria c) {
    return (root, query, cb) -> {
      HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
      Predicate p = cb.conjunction();

      if (text(c.name())) p = cb.and(p, like(cb, root.get("displayName"), c.name()));
      if (text(c.email())) p = cb.and(p, like(cb, root.get("email"), c.email()));
      if (c.userId() != null) p = cb.and(p, cb.equal(root.get("id"), c.userId()));
      if (c.role() != null) p = cb.and(p, cb.equal(root.get("role"), c.role()));
      if (c.suspended() != null) p = cb.and(p, cb.equal(root.get("suspended"), c.suspended()));
      if (c.plans() != null && !c.plans().isEmpty()) p = cb.and(p, root.get("currentPlan").in(c.plans()));

      if (c.registeredFrom() != null)
        p = cb.and(p, cb.greaterThanOrEqualTo(root.get("createdAt"), startOfDay(c.registeredFrom())));
      if (c.registeredTo() != null)
        p = cb.and(p, cb.lessThan(root.get("createdAt"), startOfDay(c.registeredTo().plusDays(1))));
      // 「登録からの経過日数」は絶対日付の代わりに相対日数で指定する場合のフィルタ。
      // 経過日数の最小値(=より古い登録)は登録日の上限、最大値(=より新しい登録)は下限に変換する。
      if (c.registeredDaysAgoMin() != null)
        p = cb.and(p, cb.lessThanOrEqualTo(root.get("createdAt"), daysAgo(c.registeredDaysAgoMin())));
      if (c.registeredDaysAgoMax() != null)
        p = cb.and(p, cb.greaterThanOrEqualTo(root.get("createdAt"), daysAgo(c.registeredDaysAgoMax())));

      if (c.firstLoginFrom() != null)
        p = cb.and(p, cb.greaterThanOrEqualTo(root.get("firstLoginAt"), startOfDay(c.firstLoginFrom())));
      if (c.firstLoginTo() != null)
        p = cb.and(p, cb.lessThan(root.get("firstLoginAt"), startOfDay(c.firstLoginTo().plusDays(1))));
      if (c.lastLoginFrom() != null)
        p = cb.and(p, cb.greaterThanOrEqualTo(root.get("lastLoginAt"), startOfDay(c.lastLoginFrom())));
      if (c.lastLoginTo() != null)
        p = cb.and(p, cb.lessThan(root.get("lastLoginAt"), startOfDay(c.lastLoginTo().plusDays(1))));
      if (Boolean.TRUE.equals(c.neverLoggedIn())) p = cb.and(p, cb.isNull(root.get("firstLoginAt")));
      if (Boolean.FALSE.equals(c.neverLoggedIn())) p = cb.and(p, cb.isNotNull(root.get("firstLoginAt")));

      if (c.firstPostFrom() != null || c.firstPostTo() != null) {
        Expression<LocalDateTime> firstPostAt = firstPostAtSubquery(query, cb, root);
        if (c.firstPostFrom() != null)
          p = cb.and(p, cb.greaterThanOrEqualTo(firstPostAt, startOfDay(c.firstPostFrom())));
        if (c.firstPostTo() != null)
          p = cb.and(p, cb.lessThan(firstPostAt, startOfDay(c.firstPostTo().plusDays(1))));
      }
      if (c.hasPosted() != null) {
        Subquery<Long> exists = publishedPostsSubquery(query, cb, root);
        p = cb.and(p, Boolean.TRUE.equals(c.hasPosted()) ? cb.exists(exists) : cb.not(cb.exists(exists)));
      }
      if (c.postCountMin() != null || c.postCountMax() != null) {
        Expression<Long> postCount = postCountSubquery(query, cb, root);
        if (c.postCountMin() != null) p = cb.and(p, cb.ge(postCount, (long) c.postCountMin()));
        if (c.postCountMax() != null) p = cb.and(p, cb.le(postCount, (long) c.postCountMax()));
      }

      if (Boolean.TRUE.equals(c.everPaid())) p = cb.and(p, cb.isNotNull(root.get("firstPaidAt")));
      if (Boolean.FALSE.equals(c.everPaid())) p = cb.and(p, cb.isNull(root.get("firstPaidAt")));
      if (c.firstPaidFrom() != null)
        p = cb.and(p, cb.greaterThanOrEqualTo(root.get("firstPaidAt"), startOfDay(c.firstPaidFrom())));
      if (c.firstPaidTo() != null)
        p = cb.and(p, cb.lessThan(root.get("firstPaidAt"), startOfDay(c.firstPaidTo().plusDays(1))));
      if (Boolean.TRUE.equals(c.currentlyPaid()))
        p = cb.and(p, cb.equal(root.get("currentPlan"), com.exradar.entity.PlanType.PREMIUM));
      if (Boolean.FALSE.equals(c.currentlyPaid()))
        p = cb.and(p, cb.equal(root.get("currentPlan"), com.exradar.entity.PlanType.FREE));

      if (c.paidDurationMinDays() != null || c.paidDurationMaxDays() != null) {
        // 加入期間は「開始日時が入っているユーザー」だけが対象。終了日時が無ければ
        // 現在も加入中とみなし、現在時刻までの日数で判定する。
        p = cb.and(p, cb.isNotNull(root.get("premiumPeriodStartedAt")));
        Expression<LocalDateTime> effectiveEnd =
            cb.coalesce(
                root.<LocalDateTime>get("premiumPeriodEndedAt"), cb.literal(LocalDateTime.now()));
        // durationBetween(a, b)はb-aではなくa-bを返す(H2向け生成SQLで確認済み)ため、
        // 「終了 - 開始」の正の日数を得るには終了・開始の順で渡す。
        Expression<Long> durationDays =
            hcb.durationByUnit(
                TemporalUnit.DAY,
                hcb.durationBetween(effectiveEnd, root.<LocalDateTime>get("premiumPeriodStartedAt")));
        if (c.paidDurationMinDays() != null) p = cb.and(p, cb.ge(durationDays, (long) c.paidDurationMinDays()));
        if (c.paidDurationMaxDays() != null) p = cb.and(p, cb.le(durationDays, (long) c.paidDurationMaxDays()));
      }

      return p;
    };
  }

  /** 投稿数(PUBLISHEDのみ)を数える相関サブクエリ。 */
  static Expression<Long> postCountSubquery(CriteriaQuery<?> query, CriteriaBuilder cb, Root<User> userRoot) {
    Subquery<Long> sq = query.subquery(Long.class);
    Root<ExperiencePost> pr = sq.from(ExperiencePost.class);
    sq.select(cb.count(pr))
        .where(cb.equal(pr.get("author"), userRoot), cb.equal(pr.get("status"), PostStatus.PUBLISHED));
    return sq;
  }

  /** 初回投稿日時(PUBLISHEDのみ)を求める相関サブクエリ。 */
  static Expression<LocalDateTime> firstPostAtSubquery(
      CriteriaQuery<?> query, CriteriaBuilder cb, Root<User> userRoot) {
    Subquery<LocalDateTime> sq = query.subquery(LocalDateTime.class);
    Root<ExperiencePost> pr = sq.from(ExperiencePost.class);
    sq.select(cb.least(pr.<LocalDateTime>get("createdAt")))
        .where(cb.equal(pr.get("author"), userRoot), cb.equal(pr.get("status"), PostStatus.PUBLISHED));
    return sq;
  }

  /** 最新投稿日時(PUBLISHEDのみ)を求める相関サブクエリ。 */
  static Expression<LocalDateTime> lastPostAtSubquery(
      CriteriaQuery<?> query, CriteriaBuilder cb, Root<User> userRoot) {
    Subquery<LocalDateTime> sq = query.subquery(LocalDateTime.class);
    Root<ExperiencePost> pr = sq.from(ExperiencePost.class);
    sq.select(cb.greatest(pr.<LocalDateTime>get("createdAt")))
        .where(cb.equal(pr.get("author"), userRoot), cb.equal(pr.get("status"), PostStatus.PUBLISHED));
    return sq;
  }

  /** 投稿済み/未投稿(PUBLISHEDのみ)判定用のEXISTSサブクエリ。 */
  static Subquery<Long> publishedPostsSubquery(CriteriaQuery<?> query, CriteriaBuilder cb, Root<User> userRoot) {
    Subquery<Long> sq = query.subquery(Long.class);
    Root<ExperiencePost> pr = sq.from(ExperiencePost.class);
    sq.select(pr.get("id"))
        .where(cb.equal(pr.get("author"), userRoot), cb.equal(pr.get("status"), PostStatus.PUBLISHED));
    return sq;
  }

  /** 現在の加入期間(premiumPeriodStartedAt〜COALESCE(premiumPeriodEndedAt,now))の日数。ソートでも使う。 */
  static Expression<Long> paidDurationDaysExpression(CriteriaBuilder cb, Root<User> userRoot) {
    HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
    Expression<LocalDateTime> effectiveEnd =
        cb.coalesce(
            userRoot.<LocalDateTime>get("premiumPeriodEndedAt"), cb.literal(LocalDateTime.now()));
    // durationBetween(a, b)はa-bを返す(publicなbuild()内のコメント参照)ため、
    // 「終了 - 開始」の正の日数を得るには終了・開始の順で渡す。
    return hcb.durationByUnit(
        TemporalUnit.DAY,
        hcb.durationBetween(effectiveEnd, userRoot.<LocalDateTime>get("premiumPeriodStartedAt")));
  }

  private static Predicate like(CriteriaBuilder cb, Expression<String> path, String value) {
    return cb.like(cb.lower(path), "%" + value.trim().toLowerCase() + "%");
  }

  private static boolean text(String s) {
    return s != null && !s.isBlank();
  }

  private static LocalDateTime startOfDay(LocalDate date) {
    return date.atStartOfDay();
  }

  private static LocalDateTime daysAgo(int days) {
    return LocalDateTime.now().minusDays(days);
  }
}
