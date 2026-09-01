package com.exradar.service;

import static org.assertj.core.api.Assertions.*;

import com.exradar.entity.*;
import com.exradar.exception.ForbiddenOperationException;
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InsightServiceTest {
  @Autowired InsightService insights;
  @Autowired ExperiencePostService postService;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired PasswordEncoder encoder;
  User one, two;
  Category category;

  @BeforeEach
  void setUp() {
    one =
        users.save(
            new User("insight-one@example.com", encoder.encode("password"), "一人目", Role.USER));
    two =
        users.save(
            new User("insight-two@example.com", encoder.encode("password"), "二人目", Role.USER));
    one.updateProfile("一人目", "20代", "大学", "エンジニア", "東京", "");
    two.updateProfile("二人目", "20代", "大学", "デザイナー", "東京", "");
    category = categories.save(new Category("進路", "insight-route", 80));
  }

  @Test
  void emptyPopulationUsesZeroWithoutNan() {
    var result = insights.dashboard(one.getEmail());
    assertThat(result.ownPostCount()).isZero();
    assertThat(result.basicStatistics().postCount()).isZero();
    assertThat(result.basicStatistics().averageSatisfaction()).isZero();
    assertThat(result.canSeeSimilarUsers()).isFalse();
  }

  @Test
  void aggregatesDistinctContributorsAndRoundsPercent() {
    postService.create(form(8, 3, true), one.getEmail());
    postService.create(form(9, 2, false), one.getEmail());
    postService.create(form(7, 4, true), two.getEmail());
    var s = insights.publicStatistics().getFirst();
    assertThat(s.postCount()).isEqualTo(3);
    assertThat(s.contributorCount()).isEqualTo(2);
    assertThat(s.averageSatisfaction()).isEqualTo(8.0);
    assertThat(s.chooseAgainPercentage()).isEqualTo(66.7);
  }

  @Test
  void scoreStaysWithinBoundariesAndIsExplainable() {
    var a = postService.create(form(10, 1, true), one.getEmail());
    var b = postService.create(form(10, 1, true), two.getEmail());
    assertThat(insights.postScore(a, b)).isBetween(0, 100).isEqualTo(80);
    assertThat(insights.dashboard(one.getEmail()).similarPosts())
        .allMatch(s -> s.score() >= 0 && s.score() <= 100 && !s.explanation().isBlank());
  }

  @Test
  void eachPublishedPostUnlocksOneAnalysisStage() {
    assertThat(insights.dashboard(one.getEmail()).unlockedLevel()).isZero();
    assertThatThrownBy(() -> insights.detailedStatistics(one.getEmail()))
        .isInstanceOf(ForbiddenOperationException.class);

    postService.create(form(8, 3, true), one.getEmail());
    assertThat(insights.dashboard(one.getEmail()).unlockedLevel()).isOne();
    assertThat(insights.dashboard(one.getEmail()).canSeeBasicStatistics()).isTrue();
    assertThat(insights.dashboard(one.getEmail()).canSeeSimilarUsers()).isFalse();

    postService.create(form(8, 3, true), one.getEmail());
    assertThat(insights.dashboard(one.getEmail()).unlockedLevel()).isEqualTo(2);
    assertThat(insights.dashboard(one.getEmail()).canSeeSimilarUsers()).isTrue();
    assertThatThrownBy(() -> insights.detailedStatistics(one.getEmail()))
        .isInstanceOf(ForbiddenOperationException.class);

    postService.create(form(8, 3, true), one.getEmail());
    assertThatCode(() -> insights.detailedStatistics(one.getEmail())).doesNotThrowAnyException();
    assertThatThrownBy(() -> insights.nextRoutes(one.getEmail()))
        .isInstanceOf(ForbiddenOperationException.class);

    postService.create(form(8, 3, true), one.getEmail());
    assertThatCode(() -> insights.nextRoutes(one.getEmail())).doesNotThrowAnyException();
    assertThatThrownBy(() -> insights.satisfactionTrends(one.getEmail()))
        .isInstanceOf(ForbiddenOperationException.class);

    postService.create(form(8, 3, true), one.getEmail());
    assertThatCode(() -> insights.satisfactionTrends(one.getEmail())).doesNotThrowAnyException();
    assertThat(insights.dashboard(one.getEmail()).unlockedLevel()).isEqualTo(5);
  }

  @Test
  void publicStatisticsExcludeDraftsAndSuspendedAuthors() {
    postService.create(form(8, 3, true), one.getEmail());
    var draft = form(10, 1, true);
    draft.setPublished(false);
    postService.create(draft, one.getEmail());
    postService.create(form(1, 10, false), two.getEmail());
    two.setSuspended(true);
    var s = insights.publicStatistics().getFirst();
    assertThat(s.postCount()).isOne();
    assertThat(s.contributorCount()).isOne();
    assertThat(s.averageSatisfaction()).isEqualTo(8.0);
  }

  private ExperiencePostForm form(int satisfaction, int regret, boolean again) {
    var f = new ExperiencePostForm();
    f.setCategoryId(category.getId());
    f.setTitle("進路選択のその後");
    f.setAgeAtChoice(22);
    f.setStatusAtChoice("学生");
    f.setCurrentAgeGroup("20代");
    f.setYearsElapsed(2);
    f.setSituationBefore("進路に迷った");
    f.setWorries("費用が心配だった");
    f.setAlternatives("就職する");
    f.setChoiceMade("進学した");
    f.setReason("学びたかった");
    f.setOutcome("専門性を得た");
    f.setGoodThings("仲間ができた");
    f.setDifficulties("費用が大変だった");
    f.setUnexpectedThings("興味が広がった");
    f.setSatisfaction(satisfaction);
    f.setRegret(regret);
    f.setChooseAgain(again);
    f.setAdviceToPastSelf("比較して決めよう");
    f.setPublished(true);
    return f;
  }
}
