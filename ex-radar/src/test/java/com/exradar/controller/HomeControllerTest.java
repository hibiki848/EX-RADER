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

  /**
   * トップページのmeta description/og:descriptionを、現在のヒーロー部分のコンセプト文言
   * (「悔いのない人生などない。学びのない人生などない。...」)と統一したことを確認する。
   * titleは維持されていること、canonicalが出力されることも合わせて確認する。
   */
  @Test
  void homeMetaDescriptionMatchesCurrentHeroConceptAndTitleIsUnchanged() throws Exception {
    var body = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    org.assertj.core.api.Assertions.assertThat(body)
        .contains("<title>EXレーダー | 他人の失敗・後悔から学び、判断材料にする</title>")
        .contains("EXレーダー | 他人の失敗・後悔から学び、判断材料にする\" />")
        .contains(
            "悔いのない人生などない。学びのない人生などない。自分と他人の失敗を「学び」に変え、あなたの人生を賢く導く。")
        .contains("rel=\"canonical\"")
        .doesNotContain("EX-RADER");

    // description(name="description")とog:descriptionの両方に同じ新しい文言が出ていること
    int occurrences =
        body.split(
                    "悔いのない人生などない。学びのない人生などない。自分と他人の失敗を「学び」に変え、あなたの人生を賢く導く。",
                    -1)
                .length
            - 1;
    org.assertj.core.api.Assertions.assertThat(occurrences).isEqualTo(2);
  }

  @Test
  void homeHasFaviconLinkInHead() throws Exception {
    var body = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(body)
        .contains("rel=\"icon\"", "/images/favicon/favicon.svg")
        .contains("apple-touch-icon");
  }

  /** WebSite構造化データ(JSON-LD)がname/urlを正しく含んで描画されることを確認する。 */
  @Test
  void homeRendersWebSiteStructuredDataWithNameAndUrl() throws Exception {
    var body = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(body)
        .contains("application/ld+json")
        .contains("\"@type\": \"WebSite\"")
        .contains("\"@type\": \"Organization\"")
        .contains("\"name\": \"EXレーダー\"")
        .contains("http:\\/\\/localhost\\/");
  }

  /**
   * サイトリンク候補の土台として、トップページから未ログインでも/experiences・/articlesへ
   * 通常のaタグ(href属性)で辿れることを確認する(JSクリックイベントだけの遷移ではない)。
   * アンカーテキストも「体験談を探す」「記事を読む」など内容が分かる表現になっていること。
   */
  @Test
  void homeHasNormalAnchorLinksToExperiencesAndArticlesForAnonymousUsers() throws Exception {
    var body = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(body)
        .contains("<a class=\"button\" href=\"/experiences\">体験談を探す</a>")
        .contains("href=\"/articles\">記事を読む</a>");
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

  /**
   * give to get: 「経験から得られた教訓」カードの教訓本文(learned/lesson)は、
   * 自分自身の公開体験談を1件以上投稿した閲覧者にのみ表示される。
   * 匿名・未投稿の閲覧者には教訓本文の代わりに、公開情報(大変だったこと)を使った
   * 見出しとロック表示を出す(教訓本文そのものはHTMLへ一切出力しない)。
   */
  @Test
  void referenceCardShowsLearnedTextOnlyToUsersWhoHavePublishedTheirOwn() throws Exception {
    var owner =
        users.save(new User("home-lesson-user@example.com", encoder.encode("password"), "教訓投稿者", Role.USER));
    var category = categories.save(new Category("転職", "home-lesson-category", 1));
    var f = validForm(category.getId());
    f.setTitle("タイトルはカードのメインテキストには使われないはず");
    f.setLearned("資格そのものより、挑戦する過程で自分に合う学び方を見つけることが大切だった");
    f.setDifficulties("勉強時間の確保に苦労した");
    postService.create(f, owner.getEmail());

    var anonymousBody =
        mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(anonymousBody)
        .doesNotContain("資格そのものより、挑戦する過程で自分に合う学び方を見つけることが大切だった")
        .contains("勉強時間の確保に苦労した")
        .contains("体験談を投稿すると、この経験から得られた教訓を読めます");

    var contributor =
        users.save(new User("home-lesson-reader@example.com", encoder.encode("password"), "貢献者", Role.USER));
    postService.create(validForm(category.getId()), contributor.getEmail());
    var contributorBody =
        mvc.perform(get("/").with(org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors.user(contributor.getEmail())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    org.assertj.core.api.Assertions.assertThat(contributorBody)
        .contains("資格そのものより、挑戦する過程で自分に合う学び方を見つけることが大切だった");
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

  /**
   * トップページのレスポンス本文全体に、未解放の学び本文が一切含まれないことを確認する。
   * CSSでの非表示ではなくサーバー側でModelへ渡していないことを保証したいので、
   * カード見出し・簡易表示モーダルを含むレスポンス全文に対して文字列不在をassertする。
   */
  @Test
  void anonymousHomeResponseNeverContainsWisdomTextAnywhereInBody() throws Exception {
    var owner =
        users.save(new User("home-leak-check@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "home-leak-check-category", 1));
    var f = validForm(category.getId());
    f.setLearned("特徴的な学びテキストAAAA111");
    f.setLesson("特徴的な教訓テキストBBBB222");
    postService.create(f, owner.getEmail());

    var body = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    org.assertj.core.api.Assertions.assertThat(body)
        .doesNotContain("特徴的な学びテキストAAAA111", "特徴的な教訓テキストBBBB222");
    // 匿名は未認証のため「簡易表示」モーダル自体(sec:authorize="isAuthenticated()")が
    // レンダリングされず、開くボタンも表示されないことも合わせて確認する
    org.assertj.core.api.Assertions.assertThat(body).doesNotContain("簡易表示");
  }

  /**
   * ログイン済みだが自分の体験談を投稿していない閲覧者にも、簡易表示モーダルの
   * 学び部分(経験して分かったこと)は表示されず、投稿導線のみが表示される。
   */
  @Test
  void loggedInButNotPostedUserSeesLockedTeaserInHomeSummaryModal() throws Exception {
    var owner =
        users.save(new User("home-locked-modal-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var reader =
        users.save(new User("home-locked-modal-reader@example.com", encoder.encode("password"), "未貢献者", Role.USER));
    var category = categories.save(new Category("転職", "home-locked-modal-category", 1));
    var f = validForm(category.getId());
    f.setLearned("未貢献者には見せない学び本文");
    postService.create(f, owner.getEmail());

    var body =
        mvc.perform(
                get("/")
                    .with(
                        org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.user(reader.getEmail())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    org.assertj.core.api.Assertions.assertThat(body)
        .doesNotContain("未貢献者には見せない学び本文")
        .contains("あなたの経験を1つ投稿すると読めます");
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
