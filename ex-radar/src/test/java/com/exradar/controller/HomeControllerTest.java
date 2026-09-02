package com.exradar.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Article;
import com.exradar.entity.Category;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.ArticleRepository;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.UserRepository;
import com.exradar.service.ExperiencePostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** ホームページの「新着記事」セクションと、ヘッダーから削除した「失敗から学ぶ」リンクの検証。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HomeControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ArticleRepository articles;
  @Autowired ExperiencePostService postService;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired PasswordEncoder encoder;

  @Test
  void headerNoLongerHasFailureLearningLink() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("失敗から学ぶ"))));
  }

  @Test
  void homeShowsOnlyLatestThreePublishedArticlesAndNeverDrafts() throws Exception {
    // 公開記事を古い順に4件、下書き記事を1件用意する
    for (int i = 1; i <= 4; i++) {
      var a = new Article("公開記事" + i, "home-latest-article-" + i, "概要" + i, "本文");
      a.publish();
      articles.save(a);
      Thread.sleep(5); // publishedAtの順序をテストで確実に区別するため
    }
    var draft = new Article("下書き記事タイトル", "home-latest-draft", "概要", "本文");
    articles.save(draft);

    var result =
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // 下書きは絶対に表示されない
    org.assertj.core.api.Assertions.assertThat(result).doesNotContain("下書き記事タイトル");
    // 最新3件(公開記事4・3・2)のみ表示され、最も古い公開記事1は表示されない
    org.assertj.core.api.Assertions.assertThat(result)
        .contains("公開記事4")
        .contains("公開記事3")
        .contains("公開記事2")
        .doesNotContain("公開記事1");
  }

  @Test
  void homeMoreButtonLinksToExistingArticleListPage() throws Exception {
    var a = new Article("もっと見る確認用記事", "home-more-button-check", "概要", "本文");
    a.publish();
    articles.save(a);

    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("href=\"/articles\"")));
  }

  @Test
  void referenceCardShowsLearnedTextInsteadOfTitle() throws Exception {
    var owner =
        users.save(new User("home-lesson-user@example.com", encoder.encode("password"), "教訓投稿者", Role.USER));
    var category = categories.save(new Category("転職", "home-lesson-category", 1));
    var f = validForm(category.getId());
    f.setTitle("タイトルはカードのメインテキストには使われないはず");
    f.setLearned("資格そのものより、挑戦する過程で自分に合う学び方を見つけることが大切だった");
    postService.create(f, owner.getEmail());

    var result = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    // 「経験から得られた教訓」カードには教訓本文がメインテキストとして表示される
    // (「新着の体験談」カードには従来どおりタイトルが表示されるため、タイトル自体は
    // ページ上に残るが、教訓表示位置にタイトルがそのまま出ていないことを確認する)
    org.assertj.core.api.Assertions.assertThat(result)
        .contains("<h3>資格そのものより、挑戦する過程で自分に合う学び方を見つけることが大切だった</h3>");
    int occurrences = result.split("タイトルはカードのメインテキストには使われないはず", -1).length - 1;
    org.assertj.core.api.Assertions.assertThat(occurrences)
        .as("タイトルは新着の体験談カードにのみ表示され、教訓カードには表示されない")
        .isEqualTo(1);
  }

  @Test
  void referenceCardFallsBackGracefullyWhenLearnedAndLessonAreBothBlank() throws Exception {
    var owner =
        users.save(
            new User("home-lesson-fallback@example.com", encoder.encode("password"), "教訓未記入者", Role.USER));
    var category = categories.save(new Category("転職", "home-lesson-fallback-category", 1));
    var f = validForm(category.getId());
    // learned/lessonをどちらも未入力のまま(古い投稿を想定)保存する
    postService.create(f, owner.getEmail());

    // 画面エラー(500)にならず、正常にホームが表示されること
    mvc.perform(get("/")).andExpect(status().isOk());
  }

  @Test
  void homeDatesUseJapaneseYearMonthDayFormat() throws Exception {
    var owner =
        users.save(new User("home-date-format@example.com", encoder.encode("password"), "日付確認者", Role.USER));
    var category = categories.save(new Category("転職", "home-date-format-category", 1));
    postService.create(validForm(category.getId()), owner.getEmail());

    var result = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    var expected =
        java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日").format(java.time.LocalDate.now());
    org.assertj.core.api.Assertions.assertThat(result).contains(expected);
  }

  private ExperiencePostForm validForm(Long categoryId) {
    var f = new ExperiencePostForm();
    f.setCategoryId(categoryId);
    f.setTitle("ホーム画面確認用の体験談");
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
