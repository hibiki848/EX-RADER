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
    var violations = validator.validate(f);
    assertThat(violations)
        .anyMatch(v -> v.getMessage().equals("タイトルを入力してください"))
        .anyMatch(v -> v.getMessage().equals("カテゴリを選択してください"));
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
    draft.setPublished(false);
    service.create(draft, owner.getEmail());
    var criteria =
        new com.exradar.dto.ExperienceSearchCriteria(
            "未経験", category.getId(), "IT", null, null, null, null, null, 8, 3, null, null, true);
    var result = service.search(criteria, 0, "latest");
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
    draft.setPublished(false);
    service.create(draft, owner.getEmail());
    assertThat(service.canReadExperiences(owner.getEmail())).isFalse();

    service.create(valid(), owner.getEmail());
    assertThat(service.canReadExperiences(owner.getEmail())).isTrue();
    assertThat(service.canReadExperiences(other.getEmail())).isFalse();
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
    f.setPublished(true);
    return f;
  }
}
