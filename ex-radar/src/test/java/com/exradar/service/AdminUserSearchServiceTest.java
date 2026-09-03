package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.exradar.dto.AdminUserSearchCriteria;
import com.exradar.dto.AdminUserSortField;
import com.exradar.dto.AdminUserSummaryDto;
import com.exradar.entity.Category;
import com.exradar.entity.PlanType;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminUserSearchServiceTest {
  @Autowired AdminUserSearchService service;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired ExperiencePostService postService;
  @Autowired PasswordEncoder encoder;
  @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;

  Category category;

  @BeforeEach
  void setup() {
    category = categories.save(new Category("転職", "admin-search-category", 1));
  }

  private User save(String email, String name) {
    return users.save(new User(email, encoder.encode("password123"), name, Role.USER));
  }

  private AdminUserSearchCriteria empty() {
    return AdminUserSearchCriteria.empty();
  }

  private AdminUserSearchCriteria with(java.util.function.UnaryOperator<AdminUserSearchCriteria> mutator) {
    return mutator.apply(empty());
  }

  private void publish(User author, String title) {
    var f = new ExperiencePostForm();
    f.setCategoryId(category.getId());
    f.setTitle(title);
    f.setSituationBefore("状況");
    f.setWorries("悩み");
    f.setAlternatives("選択肢");
    f.setChoiceMade("選んだこと");
    f.setReason("理由");
    f.setOutcome("結果");
    f.setGoodThings("良かったこと");
    f.setDifficulties("大変だったこと");
    f.setUnexpectedThings("想定外だったこと");
    f.setSatisfaction(8);
    f.setRegret(2);
    f.setAdviceToPastSelf("アドバイス");
    postService.create(f, author.getEmail());
  }

  // ---- 名前検索 ----
  @Test
  void filtersByNamePartialMatch() {
    save("name-match1@example.com", "山田太郎");
    save("name-match2@example.com", "鈴木花子");

    var result =
        service.search(
            criteriaWith(c -> c.withName("山田")), 0, AdminUserSortField.REGISTERED_AT, Sort.Direction.DESC);

    assertThat(result.getContent()).extracting(AdminUserSummaryDto::displayName).containsExactly("山田太郎");
  }

  // ---- プラン絞り込み ----
  @Test
  void filtersByPlan() {
    var free = save("plan-free@example.com", "無料ユーザー");
    var premium = save("plan-premium@example.com", "有料ユーザー");
    premium.changePlan(PlanType.PREMIUM, LocalDateTime.now().minusDays(5));

    var result =
        service.search(
            criteriaWith(c -> c.withPlans(Set.of(PlanType.PREMIUM))),
            0,
            AdminUserSortField.REGISTERED_AT,
            Sort.Direction.DESC);

    assertThat(result.getContent()).extracting(AdminUserSummaryDto::email).containsExactly(premium.getEmail());
  }

  // ---- 登録日範囲 ----
  @Test
  void filtersByRegisteredDateRange() {
    var recent = save("registered-recent@example.com", "最近登録");
    var old = save("registered-old@example.com", "昔登録");
    reflectivelySetCreatedAt(old, LocalDateTime.now().minusDays(100));

    var result =
        service.search(
            criteriaWith(
                c -> c.withRegisteredFrom(LocalDate.now().minusDays(1)).withEmailPrefix("registered-")),
            0,
            AdminUserSortField.REGISTERED_AT,
            Sort.Direction.DESC);

    assertThat(result.getContent()).extracting(AdminUserSummaryDto::email).containsExactly(recent.getEmail());
  }

  // ---- 初回投稿日 ----
  @Test
  void filtersByFirstPostDateRange() {
    var poster = save("first-post-date@example.com", "投稿者");
    publish(poster, "初回投稿日確認用");
    save("no-post-for-date@example.com", "無投稿");

    var result =
        service.search(
            criteriaWith(c -> c.withFirstPostFrom(LocalDate.now())),
            0,
            AdminUserSortField.REGISTERED_AT,
            Sort.Direction.DESC);

    assertThat(result.getContent()).extracting(AdminUserSummaryDto::email).containsExactly(poster.getEmail());
  }

  // ---- 未投稿ユーザー ----
  @Test
  void filtersNotPostedUsers() {
    var poster = save("posted-user@example.com", "投稿済み");
    publish(poster, "未投稿フィルタ確認用");
    var nonPoster = save("not-posted-user@example.com", "未投稿");

    var result =
        service.search(
            criteriaWith(c -> c.withHasPosted(false).withEmailPrefix("posted-user")),
            0,
            AdminUserSortField.REGISTERED_AT,
            Sort.Direction.DESC);

    assertThat(result.getContent()).extracting(AdminUserSummaryDto::email).containsExactly(nonPoster.getEmail());
  }

  // ---- 投稿済みユーザー ----
  @Test
  void filtersPostedUsers() {
    var poster = save("posted-user2@example.com", "投稿済み2");
    publish(poster, "投稿済みフィルタ確認用");
    save("not-posted-user2@example.com", "未投稿2");

    var result =
        service.search(
            criteriaWith(c -> c.withHasPosted(true)), 0, AdminUserSortField.REGISTERED_AT, Sort.Direction.DESC);

    assertThat(result.getContent()).extracting(AdminUserSummaryDto::email).containsExactly(poster.getEmail());
  }

  // ---- 投稿数min/max ----
  @Test
  void filtersByPostCountRange() {
    var twoPosts = save("two-posts@example.com", "2件投稿");
    publish(twoPosts, "2件投稿その1");
    publish(twoPosts, "2件投稿その2");
    var onePost = save("one-post@example.com", "1件投稿");
    publish(onePost, "1件投稿");

    var result =
        service.search(
            criteriaWith(c -> c.withPostCountMin(2)), 0, AdminUserSortField.REGISTERED_AT, Sort.Direction.DESC);

    assertThat(result.getContent()).extracting(AdminUserSummaryDto::email).containsExactly(twoPosts.getEmail());
  }

  // ---- 初回ログイン日 ----
  @Test
  void filtersByFirstLoginDateRange() {
    var loggedInToday = save("login-today@example.com", "今日ログイン");
    loggedInToday.recordLogin(LocalDateTime.now());
    var loggedInLongAgo = save("login-old@example.com", "昔ログイン");
    loggedInLongAgo.recordLogin(LocalDateTime.now().minusDays(100));

    var result =
        service.search(
            criteriaWith(c -> c.withFirstLoginFrom(LocalDate.now())),
            0,
            AdminUserSortField.REGISTERED_AT,
            Sort.Direction.DESC);

    assertThat(result.getContent())
        .extracting(AdminUserSummaryDto::email)
        .containsExactly(loggedInToday.getEmail());
  }

  // ---- 最終ログイン日 ----
  @Test
  void filtersByLastLoginDateRange() {
    var user = save("last-login-check@example.com", "最終ログイン確認");
    user.recordLogin(LocalDateTime.now().minusDays(10));
    user.recordLogin(LocalDateTime.now());
    var neverActiveSince = save("last-login-old@example.com", "最終ログイン古い");
    neverActiveSince.recordLogin(LocalDateTime.now().minusDays(50));

    var result =
        service.search(
            criteriaWith(c -> c.withLastLoginFrom(LocalDate.now())),
            0,
            AdminUserSortField.REGISTERED_AT,
            Sort.Direction.DESC);

    assertThat(result.getContent()).extracting(AdminUserSummaryDto::email).containsExactly(user.getEmail());
  }

  @Test
  void filtersNeverLoggedIn() {
    var neverLoggedIn = save("never-login@example.com", "未ログイン");
    var loggedIn = save("has-login@example.com", "ログイン済み");
    loggedIn.recordLogin(LocalDateTime.now());

    var result =
        service.search(
            criteriaWith(c -> c.withNeverLoggedIn(true).withEmailPrefix("-login@")),
            0,
            AdminUserSortField.REGISTERED_AT,
            Sort.Direction.DESC);

    assertThat(result.getContent())
        .extracting(AdminUserSummaryDto::email)
        .containsExactly(neverLoggedIn.getEmail());
  }

  // ---- 有料加入有無 ----
  @Test
  void filtersByEverPaid() {
    var everPaid = save("ever-paid@example.com", "加入経験あり");
    everPaid.changePlan(PlanType.PREMIUM, LocalDateTime.now().minusDays(30));
    everPaid.changePlan(PlanType.FREE, LocalDateTime.now().minusDays(5));
    var neverPaid = save("never-paid@example.com", "加入経験なし");

    var result =
        service.search(
            criteriaWith(c -> c.withEverPaid(true)), 0, AdminUserSortField.REGISTERED_AT, Sort.Direction.DESC);

    assertThat(result.getContent()).extracting(AdminUserSummaryDto::email).containsExactly(everPaid.getEmail());
  }

  // ---- 有料加入期間 ----
  @Test
  void filtersByPaidDurationRange() {
    var longSubscriber = save("long-subscriber@example.com", "長期加入者");
    longSubscriber.changePlan(PlanType.PREMIUM, LocalDateTime.now().minusDays(100));
    var shortSubscriber = save("short-subscriber@example.com", "短期加入者");
    shortSubscriber.changePlan(PlanType.PREMIUM, LocalDateTime.now().minusDays(2));

    var result =
        service.search(
            criteriaWith(c -> c.withPaidDurationMinDays(30)),
            0,
            AdminUserSortField.REGISTERED_AT,
            Sort.Direction.DESC);

    assertThat(result.getContent())
        .extracting(AdminUserSummaryDto::email)
        .containsExactly(longSubscriber.getEmail());
  }

  @Test
  void sortsByPaidDurationAscendingAndDescending() {
    var longSubscriber = save("sort-duration-long@example.com", "長期");
    longSubscriber.changePlan(PlanType.PREMIUM, LocalDateTime.now().minusDays(100));
    var shortSubscriber = save("sort-duration-short@example.com", "短期");
    shortSubscriber.changePlan(PlanType.PREMIUM, LocalDateTime.now().minusDays(2));

    var desc =
        service.search(
            sameEmailPrefix("sort-duration"), 0, AdminUserSortField.PAID_DURATION_DAYS, Sort.Direction.DESC);
    assertThat(desc.getContent())
        .extracting(AdminUserSummaryDto::email)
        .containsExactly(longSubscriber.getEmail(), shortSubscriber.getEmail());
    // 降順1件目(最長)の加入期間が実際に約100日であることも確認し、durationBetweenの
    // 引数順(a-bかb-aか)を取り違えて正負が逆転していないことを検証する。
    assertThat(desc.getContent().get(0).paidDurationDays()).isBetween(99L, 101L);

    var asc =
        service.search(
            sameEmailPrefix("sort-duration"), 0, AdminUserSortField.PAID_DURATION_DAYS, Sort.Direction.ASC);
    assertThat(asc.getContent())
        .extracting(AdminUserSummaryDto::email)
        .containsExactly(shortSubscriber.getEmail(), longSubscriber.getEmail());
  }

  // ---- 複数条件AND ----
  @Test
  void combinesMultipleConditionsWithAnd() {
    // 登録30日以内 AND 無料プラン AND 投稿0件、に一致するユーザーだけが返る
    var matches = save("and-match@example.com", "AND一致");

    var wrongPlan = save("and-wrong-plan@example.com", "AND不一致(有料)");
    wrongPlan.changePlan(PlanType.PREMIUM, LocalDateTime.now().minusDays(1));

    var wrongPostCount = save("and-wrong-posts@example.com", "AND不一致(投稿あり)");
    publish(wrongPostCount, "AND不一致確認用投稿");

    var wrongDate = save("and-wrong-date@example.com", "AND不一致(登録古い)");
    reflectivelySetCreatedAt(wrongDate, LocalDateTime.now().minusDays(60));

    var result =
        service.search(
            criteriaWith(
                c ->
                    c.withRegisteredDaysAgoMax(30)
                        .withPlans(Set.of(PlanType.FREE))
                        .withPostCountMax(0)
                        .withEmailPrefix("and-")),
            0,
            AdminUserSortField.REGISTERED_AT,
            Sort.Direction.DESC);

    assertThat(result.getContent()).extracting(AdminUserSummaryDto::email).containsExactly(matches.getEmail());
  }

  // ---- 各ソート昇順/降順 ----
  @Test
  void sortsByRegisteredAtAscendingAndDescending() {
    var first = save("sort-first@example.com", "先");
    var second = save("sort-second@example.com", "後");
    reflectivelySetCreatedAt(first, LocalDateTime.now().minusDays(10));
    reflectivelySetCreatedAt(second, LocalDateTime.now().minusDays(1));

    var asc =
        service.search(sameCategoryOnly(), 0, AdminUserSortField.REGISTERED_AT, Sort.Direction.ASC);
    assertThat(asc.getContent())
        .extracting(AdminUserSummaryDto::email)
        .containsSubsequence(first.getEmail(), second.getEmail());

    var desc =
        service.search(sameCategoryOnly(), 0, AdminUserSortField.REGISTERED_AT, Sort.Direction.DESC);
    assertThat(desc.getContent())
        .extracting(AdminUserSummaryDto::email)
        .containsSubsequence(second.getEmail(), first.getEmail());
  }

  @Test
  void sortsByPostCountAscendingAndDescending() {
    var many = save("sort-postcount-many@example.com", "投稿多い");
    publish(many, "並び替え確認1");
    publish(many, "並び替え確認2");
    var few = save("sort-postcount-few@example.com", "投稿少ない");
    publish(few, "並び替え確認3");

    var desc = service.search(sameEmailPrefix("sort-postcount"), 0, AdminUserSortField.POST_COUNT, Sort.Direction.DESC);
    assertThat(desc.getContent()).extracting(AdminUserSummaryDto::email).containsExactly(many.getEmail(), few.getEmail());

    var asc = service.search(sameEmailPrefix("sort-postcount"), 0, AdminUserSortField.POST_COUNT, Sort.Direction.ASC);
    assertThat(asc.getContent()).extracting(AdminUserSummaryDto::email).containsExactly(few.getEmail(), many.getEmail());
  }

  // ---- nullを含むソート(NULLS LAST) ----
  @Test
  void sortingByFirstLoginAtPutsNullsLastRegardlessOfDirection() {
    var loggedIn = save("nulls-last-logged-in@example.com", "ログイン済み");
    loggedIn.recordLogin(LocalDateTime.now());
    var neverLoggedIn = save("nulls-last-never@example.com", "未ログイン");

    var desc =
        service.search(
            sameEmailPrefix("nulls-last"), 0, AdminUserSortField.FIRST_LOGIN_AT, Sort.Direction.DESC);
    assertThat(desc.getContent())
        .extracting(AdminUserSummaryDto::email)
        .containsExactly(loggedIn.getEmail(), neverLoggedIn.getEmail());

    var asc =
        service.search(
            sameEmailPrefix("nulls-last"), 0, AdminUserSortField.FIRST_LOGIN_AT, Sort.Direction.ASC);
    assertThat(asc.getContent())
        .extracting(AdminUserSummaryDto::email)
        .containsExactly(loggedIn.getEmail(), neverLoggedIn.getEmail());
  }

  // ---- ページングとの組み合わせ ----
  @Test
  void combinesWithPaging() {
    for (int i = 0; i < 3; i++) {
      var u = save("paging-user" + i + "@example.com", "ページング" + i);
      reflectivelySetCreatedAt(u, LocalDateTime.now().minusDays(i));
    }

    var criteria = sameEmailPrefix("paging-user");
    // AdminUserSearchServiceのページサイズは固定(DEFAULT_PAGE_SIZE=20)のため、
    // 3件だけ作成し、1ページ目に全件収まること・totalElementsが正しいことを確認する
    // (件数が多い場合の実際のページ送りはExperiencePostServiceの検索一覧側で
    // 既にDB側ページングとして検証済みのパターンを踏襲している)。
    var result = service.search(criteria, 0, AdminUserSortField.REGISTERED_AT, Sort.Direction.DESC);
    assertThat(result.getTotalElements()).isEqualTo(3);
    assertThat(result.getContent()).hasSize(3);
    assertThat(result.getNumber()).isZero();
  }

  /**
   * BaseEntity#createdAtは@Column(updatable=false)のため、エンティティのフィールドを
   * 書き換えて保存するだけでは(通常のUPDATE文にcreated_atが含まれず)実際には
   * 変化しない。テストで過去日付のユーザーを用意するため、JPQLの一括UPDATE
   * (updatable=falseの制約を受けない)で直接書き換え、以後の検索が確実に
   * 更新後の値を読むようentityManager.clear()で第一級キャッシュを捨てる。
   */
  private void reflectivelySetCreatedAt(User user, LocalDateTime value) {
    users.flush();
    entityManager
        .createQuery("update User u set u.createdAt = :value where u.id = :id")
        .setParameter("value", value)
        .setParameter("id", user.getId())
        .executeUpdate();
    entityManager.clear();
  }

  private AdminUserSearchCriteria sameCategoryOnly() {
    return criteriaWith(c -> c.withEmailPrefix("sort-"));
  }

  private AdminUserSearchCriteria sameEmailPrefix(String prefix) {
    return criteriaWith(c -> c.withEmailPrefix(prefix));
  }

  private AdminUserSearchCriteria criteriaWith(java.util.function.UnaryOperator<Builder> mutator) {
    return mutator.apply(new Builder()).build();
  }

  /** テストの可読性のためのビルダー(AdminUserSearchCriteriaは25引数recordのため)。 */
  private static final class Builder {
    private String name;
    private String email;
    private Set<PlanType> plans;
    private LocalDate registeredFrom;
    private Integer registeredDaysAgoMax;
    private LocalDate firstPostFrom;
    private Boolean hasPosted;
    private Integer postCountMin;
    private Integer postCountMax;
    private LocalDate firstLoginFrom;
    private LocalDate lastLoginFrom;
    private Boolean neverLoggedIn;
    private Boolean everPaid;
    private Integer paidDurationMinDays;

    Builder withName(String v) {
      name = v;
      return this;
    }

    Builder withEmailPrefix(String v) {
      email = v;
      return this;
    }

    Builder withPlans(Set<PlanType> v) {
      plans = v;
      return this;
    }

    Builder withRegisteredFrom(LocalDate v) {
      registeredFrom = v;
      return this;
    }

    Builder withRegisteredDaysAgoMax(int v) {
      registeredDaysAgoMax = v;
      return this;
    }

    Builder withFirstPostFrom(LocalDate v) {
      firstPostFrom = v;
      return this;
    }

    Builder withHasPosted(boolean v) {
      hasPosted = v;
      return this;
    }

    Builder withPostCountMin(int v) {
      postCountMin = v;
      return this;
    }

    Builder withPostCountMax(int v) {
      postCountMax = v;
      return this;
    }

    Builder withFirstLoginFrom(LocalDate v) {
      firstLoginFrom = v;
      return this;
    }

    Builder withLastLoginFrom(LocalDate v) {
      lastLoginFrom = v;
      return this;
    }

    Builder withNeverLoggedIn(boolean v) {
      neverLoggedIn = v;
      return this;
    }

    Builder withEverPaid(boolean v) {
      everPaid = v;
      return this;
    }

    Builder withPaidDurationMinDays(int v) {
      paidDurationMinDays = v;
      return this;
    }

    AdminUserSearchCriteria build() {
      return new AdminUserSearchCriteria(
          name,
          email,
          null,
          null,
          null,
          plans,
          registeredFrom,
          null,
          null,
          registeredDaysAgoMax,
          firstLoginFrom,
          null,
          lastLoginFrom,
          null,
          neverLoggedIn,
          firstPostFrom,
          null,
          hasPosted,
          postCountMin,
          postCountMax,
          everPaid,
          null,
          null,
          null,
          paidDurationMinDays,
          null);
    }
  }

}
