package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.exradar.entity.Category;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.repository.UserBenefitRepository;
import com.exradar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 投稿数マイルストーム達成による特典自動付与(RewardService)の検証。
 * 要件どおりの初期設定(1件→20%OFF、3件→30%OFF、5件→50%OFF、10件以降10件ごと→1か月無料)が
 * V19マイグレーションで実際に投入されていることを前提に、DB駆動での判定を確認する。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RewardServiceTest {
  @Autowired RewardService rewardService;
  @Autowired ExperiencePostService postService;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired ExperiencePostRepository posts;
  @Autowired UserBenefitRepository userBenefits;
  @Autowired PasswordEncoder encoder;

  private User newUser(String email) {
    return users.save(new User(email, encoder.encode("password"), "投稿者", Role.USER));
  }

  /**
   * ExperiencePostController.create()が実際に行っている「投稿保存→RewardService評価」の
   * 呼び出し順序を1件ずつ再現する(RewardServiceの自動評価はController層が担う設計のため、
   * ExperiencePostServiceを直接呼ぶだけのテストではここを明示的に呼ぶ必要がある)。
   */
  private void publish(User author, Category category, String title) {
    var f = validForm(category.getId(), title);
    postService.create(f, author.getEmail());
    rewardService.evaluateAndGrant(author);
  }

  @Test
  void firstPublishedPostGrantsDiscount20() {
    var author = newUser("reward-1post@example.com");
    var category = categories.save(new Category("転職", "reward-1post-category", 1));
    var f = validForm(category.getId(), "1件目の投稿");
    postService.create(f, author.getEmail());

    var granted = rewardService.evaluateAndGrant(author);
    assertThat(granted).hasSize(1);
    assertThat(granted.get(0).getBenefitNameSnapshot()).isEqualTo("プレミアム20%OFF");
    assertThat(granted.get(0).getDiscountPercentSnapshot()).isEqualTo(20);
    assertThat(userBenefits.existsByRewardGrantKey("POST_COUNT:" + author.getId() + ":1")).isTrue();
  }

  @Test
  void thirdPublishedPostGrantsDiscount30WithoutRegrantingFirst() {
    var author = newUser("reward-3posts@example.com");
    var category = categories.save(new Category("転職", "reward-3posts-category", 1));
    for (int i = 1; i <= 3; i++) publish(author, category, "投稿" + i);

    var granted = rewardService.evaluateAndGrant(author);
    // 1件目・3件目それぞれの初回評価はcreate()経由で既に走っているため、ここでは
    // まとめて評価しても「新たに達成した分」だけが返る(2回目のevaluateAndGrant呼び出しでは0件)。
    assertThat(granted).isEmpty();
    var all = userBenefits.findByUserIdOrderByGrantedAtDesc(author.getId());
    assertThat(all).extracting(b -> b.getBenefitDefinition().getCode())
        .containsExactlyInAnyOrder("DISCOUNT_20", "DISCOUNT_30");
  }

  @Test
  void fifthPublishedPostGrantsDiscount50() {
    var author = newUser("reward-5posts@example.com");
    var category = categories.save(new Category("転職", "reward-5posts-category", 1));
    for (int i = 1; i <= 5; i++) publish(author, category, "投稿" + i);

    var all = userBenefits.findByUserIdOrderByGrantedAtDesc(author.getId());
    assertThat(all).extracting(b -> b.getBenefitDefinition().getCode())
        .contains("DISCOUNT_20", "DISCOUNT_30", "DISCOUNT_50");
  }

  @Test
  void tenthPublishedPostGrantsOneFreeMonth() {
    var author = newUser("reward-10posts@example.com");
    var category = categories.save(new Category("転職", "reward-10posts-category", 1));
    for (int i = 1; i <= 10; i++) publish(author, category, "投稿" + i);

    var freeMonthGrants =
        userBenefits.findByUserIdOrderByGrantedAtDesc(author.getId()).stream()
            .filter(b -> "FREE_MONTH".equals(b.getBenefitDefinition().getCode()))
            .toList();
    assertThat(freeMonthGrants).hasSize(1);
    assertThat(freeMonthGrants.get(0).getFreeMonthsSnapshot()).isEqualTo(1);
  }

  @Test
  void twentiethPublishedPostGrantsASecondFreeMonth() {
    var author = newUser("reward-20posts@example.com");
    var category = categories.save(new Category("転職", "reward-20posts-category", 1));
    for (int i = 1; i <= 20; i++) publish(author, category, "投稿" + i);

    var freeMonthGrants =
        userBenefits.findByUserIdOrderByGrantedAtDesc(author.getId()).stream()
            .filter(b -> "FREE_MONTH".equals(b.getBenefitDefinition().getCode()))
            .toList();
    assertThat(freeMonthGrants).hasSize(2);
    assertThat(userBenefits.existsByRewardGrantKey("POST_COUNT:" + author.getId() + ":20")).isTrue();
  }

  @Test
  void thirtiethPublishedPostGrantsAThirdFreeMonthContinuingTheTenPostInterval() {
    var author = newUser("reward-30posts@example.com");
    var category = categories.save(new Category("転職", "reward-30posts-category", 1));
    for (int i = 1; i <= 30; i++) publish(author, category, "投稿" + i);

    var freeMonthGrants =
        userBenefits.findByUserIdOrderByGrantedAtDesc(author.getId()).stream()
            .filter(b -> "FREE_MONTH".equals(b.getBenefitDefinition().getCode()))
            .toList();
    assertThat(freeMonthGrants).hasSize(3);
    assertThat(userBenefits.existsByRewardGrantKey("POST_COUNT:" + author.getId() + ":30")).isTrue();
  }

  @Test
  void sameAchievementIsNeverGrantedTwiceEvenWhenEvaluatedRepeatedly() {
    var author = newUser("reward-dedup@example.com");
    var category = categories.save(new Category("転職", "reward-dedup-category", 1));
    publish(author, category, "1件目");

    // create()内部で既に1回評価済み。明示的に何度呼んでも増えない。
    rewardService.evaluateAndGrant(author);
    rewardService.evaluateAndGrant(author);
    rewardService.evaluateAndGrant(author);

    var count = userBenefits.findByUserIdOrderByGrantedAtDesc(author.getId()).size();
    assertThat(count).isEqualTo(1);
  }

  /** 下書き・削除済み投稿は特典対象投稿数にカウントされない。 */
  @Test
  void draftAndDeletedPostsAreNotCountedTowardMilestones() {
    var author = newUser("reward-invalid-posts@example.com");
    var category = categories.save(new Category("転職", "reward-invalid-category", 1));

    var draftForm = validForm(category.getId(), "下書きのまま");
    postService.createDraft(draftForm, author.getEmail());

    var toDeleteForm = validForm(category.getId(), "後で削除される投稿");
    var toDelete = postService.create(toDeleteForm, author.getEmail());
    postService.delete(toDelete.getId(), author.getEmail());

    assertThat(rewardService.eligiblePostCount(author.getId())).isZero();
    assertThat(rewardService.evaluateAndGrant(author)).isEmpty();
  }

  /** 管理者による非公開対応(hideByModeration)を受けた投稿はDRAFT扱いとなり、以後カウントから外れる。 */
  @Test
  void moderatedPostsAreExcludedFromTheCount() {
    var author = newUser("reward-moderated@example.com");
    var admin = users.save(new User("reward-moderated-admin@example.com", encoder.encode("password"), "管理者", Role.ADMIN));
    var category = categories.save(new Category("転職", "reward-moderated-category", 1));
    var post = postService.create(validForm(category.getId(), "不正投稿疑い"), author.getEmail());
    assertThat(rewardService.eligiblePostCount(author.getId())).isEqualTo(1);

    postService.hideByModeration(post.getId(), admin.getEmail());

    assertThat(rewardService.eligiblePostCount(author.getId())).isZero();
  }

  @Test
  void progressForShowsSevenOfTenBeforeReachingNextFreeMonthMilestone() {
    var author = newUser("reward-progress@example.com");
    var category = categories.save(new Category("転職", "reward-progress-category", 1));
    for (int i = 1; i <= 7; i++) publish(author, category, "投稿" + i);

    var progress = rewardService.progressFor(author);
    assertThat(progress.currentCount()).isEqualTo(7);
    assertThat(progress.nextThreshold()).isEqualTo(10);
    assertThat(progress.postsRemaining()).isEqualTo(3);
    assertThat(progress.nextBenefitName()).isEqualTo("プレミアム1か月無料");
  }

  private ExperiencePostForm validForm(Long categoryId, String title) {
    var f = new ExperiencePostForm();
    f.setCategoryId(categoryId);
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
    return f;
  }
}
