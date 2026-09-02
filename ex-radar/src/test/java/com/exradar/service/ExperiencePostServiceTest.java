package com.exradar.service;

import static org.assertj.core.api.Assertions.*;

import com.exradar.entity.*;
import com.exradar.exception.ForbiddenOperationException;
import com.exradar.form.*;
import com.exradar.repository.*;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExperiencePostServiceTest {
  @Autowired ExperiencePostService service;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired ExperiencePostRepository posts;
  @Autowired PasswordEncoder encoder;
  @Autowired Validator validator;
  User owner, other, admin;
  Category category;

  @BeforeEach
  void setup() {
    owner = users.save(new User("owner@example.com", encoder.encode("password"), "投稿者", Role.USER));
    other = users.save(new User("other@example.com", encoder.encode("password"), "他人", Role.USER));
    admin =
        users.save(new User("admin2@example.com", encoder.encode("password"), "管理者", Role.ADMIN));
    category = categories.save(new Category("転職", "test-career", 1));
  }

  @Test
  void createsPostWithMultipleLifeEvents() {
    var f = valid();
    f.setTagNames("Java, java, #転職");
    f.getLifeEvents().add(event("1年後", "転職成功"));
    f.getLifeEvents().add(event("3年後", "リーダー就任"));
    var saved = service.create(f, owner.getEmail());
    posts.flush();
    var loaded = service.getVisible(saved.getId(), null);
    assertThat(loaded.getTitle()).isEqualTo("未経験転職のその後");
    assertThat(loaded.getLifeEvents())
        .extracting(LifeEvent::getTitle)
        .containsExactly("転職成功", "リーダー就任");
    assertThat(loaded.getTags())
        .extracting(com.exradar.entity.Tag::getName)
        .containsExactlyInAnyOrder("Java", "転職");
  }

  @Test
  void japaneseValidationRejectsMissingRequiredFields() {
    var f = new ExperiencePostForm();
    var violations = validator.validate(f, PublishValidation.class);
    assertThat(violations)
        .anyMatch(v -> v.getMessage().equals("タイトルを入力してください"))
        .anyMatch(v -> v.getMessage().equals("カテゴリを選択してください"));
  }

  @Test
  void draftValidationAllowsNearEmptyForm() {
    var f = new ExperiencePostForm();
    var violations = validator.validate(f, DraftValidation.class);
    assertThat(violations).isEmpty();
  }

  @Test
  void rejectsOtherUserButAllowsAdmin() {
    var saved = service.create(valid(), owner.getEmail());
    assertThatThrownBy(() -> service.update(saved.getId(), valid(), other.getEmail()))
        .isInstanceOf(ForbiddenOperationException.class);
    var changed = valid();
    changed.setTitle("管理者による修正");
    service.update(saved.getId(), changed, admin.getEmail());
    assertThat(service.getVisible(saved.getId(), null).getTitle()).isEqualTo("管理者による修正");
    service.delete(saved.getId(), admin.getEmail());
    assertThat(posts.findById(saved.getId())).isEmpty();
  }

  @Test
  void publicSearchReturnsCardDtoAndNeverDraft() {
    var published = valid();
    published.setTagNames("IT");
    service.create(published, owner.getEmail());
    var draft = valid();
    draft.setTitle("非公開");
    service.createDraft(draft, owner.getEmail());
    var criteria =
        new com.exradar.dto.ExperienceSearchCriteria(
            "未経験", category.getId(), "IT", null, null, null, null, null, 8, 3, null, null, true);
    var result = service.search(criteria, 0, "latest", false);
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent())
        .extracting(com.exradar.dto.ExperienceCardDto::title)
        .containsExactly("未経験転職のその後");
  }

  @Test
  void onlyUserWithPublishedExperienceUnlocksReading() {
    assertThat(service.canReadExperiences(owner.getEmail())).isFalse();
    assertThat(service.canReadExperiences(other.getEmail())).isFalse();
    assertThat(service.canReadExperiences(null)).isFalse();

    var draft = valid();
    service.createDraft(draft, owner.getEmail());
    assertThat(service.canReadExperiences(owner.getEmail())).isFalse();

    service.create(valid(), owner.getEmail());
    assertThat(service.canReadExperiences(owner.getEmail())).isTrue();
    assertThat(service.canReadExperiences(other.getEmail())).isFalse();
  }

  @Test
  void createDraftAllowsNearEmptyContentAndStaysDraft() {
    var f = new ExperiencePostForm();
    f.setTitle("下書きタイトルだけ");
    var saved = service.createDraft(f, owner.getEmail());
    assertThat(saved.getStatus()).isEqualTo(PostStatus.DRAFT);
    assertThat(saved.isPublished()).isFalse();
    assertThat(saved.getCategory()).isNull();
  }

  @Test
  void updateDraftReusesSameRecordAndNeverCreatesDuplicate() {
    var f = new ExperiencePostForm();
    f.setTitle("最初の下書き");
    var saved = service.createDraft(f, owner.getEmail());
    long countBefore = posts.count();

    var second = new ExperiencePostForm();
    second.setTitle("更新後の下書き");
    service.updateDraft(saved.getId(), second, owner.getEmail());

    assertThat(posts.count()).isEqualTo(countBefore);
    assertThat(service.getManageable(saved.getId(), owner.getEmail()).getTitle())
        .isEqualTo("更新後の下書き");
    assertThat(service.getManageable(saved.getId(), owner.getEmail()).getStatus())
        .isEqualTo(PostStatus.DRAFT);
  }

  @Test
  void publishingDraftUpdatesSameRecordToPublished() {
    var f = new ExperiencePostForm();
    f.setTitle("公開前の下書き");
    var saved = service.createDraft(f, owner.getEmail());
    long countBefore = posts.count();

    service.update(saved.getId(), valid(), owner.getEmail());

    assertThat(posts.count()).isEqualTo(countBefore);
    var published = service.getVisible(saved.getId(), null);
    assertThat(published.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    assertThat(published.getTitle()).isEqualTo("未経験転職のその後");
  }

  @Test
  void lateDraftSaveNeverRevertsPublishedBackToDraft() {
    var saved = service.create(valid(), owner.getEmail());
    assertThat(saved.getStatus()).isEqualTo(PostStatus.PUBLISHED);

    var lateAutosave = valid();
    lateAutosave.setTitle("遅延到着の自動保存");
    assertThatThrownBy(() -> service.updateDraft(saved.getId(), lateAutosave, owner.getEmail()))
        .isInstanceOf(ForbiddenOperationException.class);

    var stillPublished = service.getVisible(saved.getId(), null);
    assertThat(stillPublished.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    assertThat(stillPublished.getTitle()).isEqualTo("未経験転職のその後");
  }

  @Test
  void draftIsInvisibleAndUnmanageableToOtherUsers() {
    var f = new ExperiencePostForm();
    f.setTitle("他人には見えない下書き");
    var saved = service.createDraft(f, owner.getEmail());

    assertThatThrownBy(() -> service.getVisible(saved.getId(), other.getEmail()))
        .isInstanceOf(com.exradar.exception.ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.getManageable(saved.getId(), other.getEmail()))
        .isInstanceOf(ForbiddenOperationException.class);
    assertThatThrownBy(() -> service.updateDraft(saved.getId(), f, other.getEmail()))
        .isInstanceOf(ForbiddenOperationException.class);
  }

  @Test
  void draftIsInvisibleToAnonymousUser() {
    var f = new ExperiencePostForm();
    f.setTitle("匿名には見えない下書き");
    var saved = service.createDraft(f, owner.getEmail());

    assertThatThrownBy(() -> service.getVisible(saved.getId(), null))
        .isInstanceOf(com.exradar.exception.ResourceNotFoundException.class);
  }

  @Test
  void moderationHiddenPostIsInvisibleToAnonymousAndOtherUsersButVisibleToAdmin() {
    var saved = service.create(valid(), owner.getEmail());
    service.hideByModeration(saved.getId(), admin.getEmail());

    assertThatThrownBy(() -> service.getVisible(saved.getId(), null))
        .isInstanceOf(com.exradar.exception.ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.getVisible(saved.getId(), other.getEmail()))
        .isInstanceOf(com.exradar.exception.ResourceNotFoundException.class);
    assertThat(service.getVisible(saved.getId(), admin.getEmail()).getStatus())
        .isEqualTo(PostStatus.DRAFT);
    assertThat(service.getVisible(saved.getId(), owner.getEmail()).getStatus())
        .isEqualTo(PostStatus.DRAFT);
  }

  /**
   * 「学び」部分の解放条件: 自分自身の公開体験談を1件以上投稿しているか、
   * その投稿の本人・管理者であること。下書きだけでは解放されない(既存のgive to get
   * 判定canReadExperiencesをそのまま利用しているため)。
   */
  @Test
  void wisdomIsReadableByOwnerAdminAndUsersWithOwnPublishedPost() {
    var post = service.create(valid(), owner.getEmail());

    assertThat(service.canReadWisdom(post, owner.getEmail())).isTrue();
    assertThat(service.canReadWisdom(post, admin.getEmail())).isTrue();
    assertThat(service.canReadWisdom(post, null)).isFalse();
    assertThat(service.canReadWisdom(post, other.getEmail())).isFalse();

    var draftOnly = valid();
    draftOnly.setTitle("下書きだけの人");
    service.createDraft(draftOnly, other.getEmail());
    assertThat(service.canReadWisdom(post, other.getEmail())).isFalse();

    service.create(valid(), other.getEmail());
    assertThat(service.canReadWisdom(post, other.getEmail())).isTrue();
  }

  /**
   * ExperienceCardDto(一覧・トップページ・プロフィール等のカード表示に使う)は、
   * wisdomUnlocked=falseで生成するとlearned/lessonがnullになり、モデルへ渡す時点で
   * 学び本文を一切含まないことを確認する。search/latest/recommended/similar/byAuthorの
   * 全メソッドがこのwisdomUnlockedフラグをExperienceCardDto.fromへ正しく伝播すること。
   */
  @Test
  void cardDtoOmitsWisdomFieldsWhenLocked() {
    var f = valid();
    f.setLearned("学んだこと本文");
    f.setLesson("教訓本文");
    var saved = service.create(f, owner.getEmail());
    posts.flush();

    var criteria =
        new com.exradar.dto.ExperienceSearchCriteria(
            null, category.getId(), null, null, null, null, null, null, null, null, null, null, null);

    var lockedCard = service.search(criteria, 0, "latest", false).getContent().get(0);
    assertThat(lockedCard.learned()).isNull();
    assertThat(lockedCard.lesson()).isNull();
    // 公開部分(経験・失敗)はロック状態でも変わらず含まれる
    assertThat(lockedCard.situationBefore()).isEqualTo(saved.getSituationBefore());
    assertThat(lockedCard.difficulties()).isEqualTo(saved.getDifficulties());

    var unlockedCard = service.search(criteria, 0, "latest", true).getContent().get(0);
    assertThat(unlockedCard.learned()).isEqualTo("学んだこと本文");
    assertThat(unlockedCard.lesson()).isEqualTo("教訓本文");

    assertThat(service.latest(false).get(0).learned()).isNull();
    assertThat(service.latest(true).get(0).learned()).isEqualTo("学んだこと本文");
    assertThat(service.recommended(false).get(0).lesson()).isNull();
    assertThat(service.recommended(true).get(0).lesson()).isEqualTo("教訓本文");
    assertThat(service.byAuthor(owner.getId(), 0, false).getContent().get(0).learned()).isNull();
    assertThat(service.byAuthor(owner.getId(), 0, true).getContent().get(0).learned())
        .isEqualTo("学んだこと本文");

    var another = service.create(valid(), other.getEmail());
    assertThat(service.similar(another, false).stream().findFirst().orElseThrow().learned()).isNull();
    assertThat(service.similar(another, true).stream().findFirst().orElseThrow().learned())
        .isEqualTo("学んだこと本文");
  }

  /**
   * GoogleログインユーザーもUser.forGoogleSignupで作成される通常のUser行(email基準)
   * であるため、canReadWisdom/canReadExperiencesはLOCALアカウントと全く同じロジックで
   * 動作する。実際のGoogle APIへは一切通信せず、AuthProvider.GOOGLEのUserを直接組み立てて検証する。
   */
  @Test
  void canReadWisdomWorksIdenticallyForGoogleAuthenticatedUsers() {
    var googleReaderNoPost =
        users.save(
            User.forGoogleSignup(
                "google-reader@example.com", "google-sub-1", "Google仮表示名1", encoder.encode("random")));
    var googleContributor =
        users.save(
            User.forGoogleSignup(
                "google-contributor@example.com",
                "google-sub-2",
                "Google仮表示名2",
                encoder.encode("random")));
    var post = service.create(valid(), owner.getEmail());

    assertThat(googleReaderNoPost.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(service.canReadWisdom(post, googleReaderNoPost.getEmail())).isFalse();
    assertThat(service.canReadExperiences(googleReaderNoPost.getEmail())).isFalse();

    service.create(valid(), googleContributor.getEmail());
    assertThat(service.canReadWisdom(post, googleContributor.getEmail())).isTrue();
    assertThat(service.canReadExperiences(googleContributor.getEmail())).isTrue();
  }

  private LifeEventForm event(String age, String title) {
    var e = new LifeEventForm();
    e.setAgeLabel(age);
    e.setTitle(title);
    e.setDescription("出来事の説明");
    return e;
  }

  private ExperiencePostForm valid() {
    var f = new ExperiencePostForm();
    f.setCategoryId(category.getId());
    f.setTitle("未経験転職のその後");
    f.setAgeAtChoice(25);
    f.setStatusAtChoice("会社員");
    f.setCurrentAgeGroup("30代");
    f.setYearsElapsed(5);
    f.setSituationBefore("将来に不安がありました");
    f.setWorries("経験がないこと");
    f.setAlternatives("現職継続と進学");
    f.setChoiceMade("IT業界への転職");
    f.setReason("ものづくりが好きだから");
    f.setOutcome("現在も楽しく働いています");
    f.setGoodThings("専門性が身についたこと");
    f.setDifficulties("最初の学習");
    f.setUnexpectedThings("仲間が増えたこと");
    f.setSatisfaction(9);
    f.setRegret(2);
    f.setChooseAgain(true);
    f.setAdviceToPastSelf("小さく学習を始めよう");
    return f;
  }
}
