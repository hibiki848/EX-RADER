package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exradar.entity.Category;
import com.exradar.entity.ExperiencePost;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.repository.UserBenefitRepository;
import com.exradar.repository.UserRepository;
import java.util.List;
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
   * 30件ループ生成時、タイトルが数字の接頭辞違いだけ(例:"投稿1"と"投稿10")だと
   * DuplicatePostDetectionServiceの類似度チェックに引っかかりやすいため、
   * 最大ループ件数(30)と同数の異なる話題語を用意し、件数ごとに本文全体を
   * 十分に異ならせる。
   */
  private static final String[] REWARD_TEST_TOPICS = {
    "転職", "独立", "進学", "留学", "資格取得", "副業開始", "引っ越し", "復職", "育児休業", "起業",
    "移住", "部署異動", "休職", "退職", "出向", "兼業解禁", "婚活", "一人暮らし", "同棲解消", "卒業",
    "就職活動", "転勤", "キャリアチェンジ", "昇進", "降格", "出張続き", "帰国", "海外赴任", "開業準備", "事業売却"
  };

  private String rewardTestTitle(int index) {
    return REWARD_TEST_TOPICS[(index - 1) % REWARD_TEST_TOPICS.length] + "に関する体験" + index;
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
    for (int i = 1; i <= 3; i++) publish(author, category, rewardTestTitle(i));

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
    for (int i = 1; i <= 5; i++) publish(author, category, rewardTestTitle(i));

    var all = userBenefits.findByUserIdOrderByGrantedAtDesc(author.getId());
    assertThat(all).extracting(b -> b.getBenefitDefinition().getCode())
        .contains("DISCOUNT_20", "DISCOUNT_30", "DISCOUNT_50");
  }

  @Test
  void tenthPublishedPostGrantsOneFreeMonth() {
    var author = newUser("reward-10posts@example.com");
    var category = categories.save(new Category("転職", "reward-10posts-category", 1));
    for (int i = 1; i <= 10; i++) publish(author, category, rewardTestTitle(i));

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
    for (int i = 1; i <= 20; i++) publish(author, category, rewardTestTitle(i));

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
    for (int i = 1; i <= 30; i++) publish(author, category, rewardTestTitle(i));

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

  /**
   * DuplicatePostDetectionServiceにより実質同一内容の投稿は保存自体が拒否されるため、
   * 特典対象投稿数が水増しされることはない(RewardService単体としても、そもそも
   * 重複行がDBに存在し得ない設計になっていることの回帰確認)。
   */
  @Test
  void duplicateContentAttemptDoesNotInflateEligiblePostCount() {
    var author = newUser("reward-dup-content@example.com");
    var category = categories.save(new Category("転職", "reward-dup-content-category", 1));
    postService.create(validForm(category.getId(), "重複投稿確認用のタイトル"), author.getEmail());
    rewardService.evaluateAndGrant(author); // 1件目の評価(Controllerが行う処理を再現)

    assertThatThrownBy(
            () -> postService.create(validForm(category.getId(), "重複投稿確認用のタイトル"), author.getEmail()))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(rewardService.eligiblePostCount(author.getId())).isEqualTo(1);
    assertThat(rewardService.evaluateAndGrant(author)).isEmpty();
  }

  /**
   * 教訓必須化(ExperiencePostService.requireLesson)より前に作成された投稿を想定したケース。
   * 現在の投稿フローでは教訓なしの投稿を新規作成できないため、既存データを模してリポジトリへ
   * 直接保存し、RewardServiceが独自に教訓の有無をチェックして特典対象から除外することを確認する
   * (投稿フォーム側のバリデーションに将来バグがあっても、RewardService単体で不正な特典付与を
   * 防げることの検証)。
   */
  @Test
  void lessonlessLegacyPostsAreNotRewardEligible() {
    var author = newUser("reward-no-lesson@example.com");
    var category = categories.save(new Category("転職", "reward-no-lesson-category", 1));
    var legacyPost = new ExperiencePost(author);
    legacyPost.updateContent(
        category, "教訓なしレガシー投稿", null, null, null, null,
        "状況", "悩み", "選択肢", "選んだこと", "理由", "結果", "良かったこと", "大変だったこと", "想定外だったこと",
        8, 2, false, "アドバイス");
    legacyPost.publish();
    posts.save(legacyPost);

    assertThat(rewardService.eligiblePostCount(author.getId())).isZero();
    assertThat(rewardService.evaluateAndGrant(author)).isEmpty();
  }

  /**
   * 報酬対象条件は投稿フォームと同じく「教訓(lesson)そのものが有効であること」に統一されており、
   * 「学んだこと(learned)」が入力されていても教訓が空であれば報酬対象にはならない
   * (以前の「learned/lessonのいずれか」という条件からの仕様変更の回帰確認)。
   */
  @Test
  void postWithLearnedButBlankLessonIsNotRewardEligible() {
    var author = newUser("reward-learned-only@example.com");
    var category = categories.save(new Category("転職", "reward-learned-only-category", 1));
    var legacyPost = new ExperiencePost(author);
    legacyPost.updateContent(
        category, "学んだことのみのレガシー投稿", null, null, null, null,
        "状況", "悩み", "選択肢", "選んだこと", "理由", "結果", "良かったこと", "大変だったこと", "想定外だったこと",
        8, 2, false, "アドバイス");
    legacyPost.updateWisdom(null, "学んだことは書いてある", null, null, null, null, null, null, null, List.of());
    legacyPost.publish();
    posts.save(legacyPost);

    assertThat(rewardService.eligiblePostCount(author.getId())).isZero();
    assertThat(rewardService.evaluateAndGrant(author)).isEmpty();
  }

  /** 教訓が9文字(最小文字数10文字未満)のPUBLISHED投稿は報酬対象外。 */
  @Test
  void lessonWithNineCharactersIsNotRewardEligible() {
    var author = newUser("reward-lesson-9chars@example.com");
    var category = categories.save(new Category("転職", "reward-lesson-9chars-category", 1));
    var legacyPost = new ExperiencePost(author);
    legacyPost.updateContent(
        category, "教訓9文字のレガシー投稿", null, null, null, null,
        "状況", "悩み", "選択肢", "選んだこと", "理由", "結果", "良かったこと", "大変だったこと", "想定外だったこと",
        8, 2, false, "アドバイス");
    legacyPost.updateWisdom(null, null, null, null, null, null, "123456789", null, null, List.of());
    legacyPost.publish();
    posts.save(legacyPost);

    assertThat(rewardService.eligiblePostCount(author.getId())).isZero();
    assertThat(rewardService.evaluateAndGrant(author)).isEmpty();
  }

  /** 教訓が10文字以上の通常のPUBLISHED投稿は報酬対象になる。 */
  @Test
  void lessonWithTenOrMoreCharactersIsRewardEligible() {
    var author = newUser("reward-lesson-10chars@example.com");
    var category = categories.save(new Category("転職", "reward-lesson-10chars-category", 1));
    var f = validForm(category.getId(), "教訓10文字のタイトル");
    f.setLesson("1234567890");
    postService.create(f, author.getEmail());

    assertThat(rewardService.eligiblePostCount(author.getId())).isEqualTo(1);
    assertThat(rewardService.evaluateAndGrant(author)).hasSize(1);
  }

  @Test
  void progressForShowsSevenOfTenBeforeReachingNextFreeMonthMilestone() {
    var author = newUser("reward-progress@example.com");
    var category = categories.save(new Category("転職", "reward-progress-category", 1));
    for (int i = 1; i <= 7; i++) publish(author, category, rewardTestTitle(i));

    var progress = rewardService.progressFor(author);
    assertThat(progress.currentCount()).isEqualTo(7);
    assertThat(progress.nextThreshold()).isEqualTo(10);
    assertThat(progress.postsRemaining()).isEqualTo(3);
    assertThat(progress.nextBenefitName()).isEqualTo("プレミアム1か月無料");
  }

  /**
   * titleを本文の複数箇所へ織り込み、生成する投稿ごとの内容を十分に異ならせる
   * (新設のDuplicatePostDetectionServiceが「同一ユーザーの実質同一投稿」として
   * 誤検出しないようにするため。1〜30件のループ生成でも意味のある差分を持たせる)。
   */
  private ExperiencePostForm validForm(Long categoryId, String title) {
    var f = new ExperiencePostForm();
    f.setCategoryId(categoryId);
    f.setTitle(title);
    f.setSituationBefore("状況の詳細です。" + title + "に関する当時の背景を説明します。");
    f.setWorries("悩みの内容です。" + title + "について迷っていました。");
    f.setAlternatives("検討した選択肢です。" + title + "の代替案について整理します。");
    f.setChoiceMade("実際に選んだことです。" + title + "を選びました。");
    f.setReason("選んだ理由です。" + title + "が良いと考えたためです。");
    f.setOutcome("その後の結果です。" + title + "を選んだ結果について説明します。");
    f.setGoodThings("良かったことです。" + title + "のおかげで良い経験ができました。");
    f.setDifficulties("大変だったことです。" + title + "では苦労しました。");
    f.setUnexpectedThings("想定外だったことです。" + title + "では予想外の展開がありました。");
    f.setLesson("この経験から得た教訓です。" + title + "を通して学んだことを整理します。");
    f.setSatisfaction(8);
    f.setRegret(2);
    f.setAdviceToPastSelf("今ならこうします。" + title + "の経験を踏まえたアドバイスです。");
    return f;
  }
}
