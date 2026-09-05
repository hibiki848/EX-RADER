package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Category;
import com.exradar.entity.ExperiencePost;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.ExperiencePostRepository;
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

/**
 * 教訓まとめ(/choices)の新しいフィード形式(検索ボックス・タグ・カテゴリバー・
 * 簡易/詳細表示切り替え・ページング・空状態)の検証。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChoiceGuideFeedTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired ExperiencePostRepository posts;
  @Autowired ExperiencePostService postService;
  @Autowired PasswordEncoder encoder;

  private User contributor(String email) {
    var u = users.save(new User(email, encoder.encode("password"), "貢献者", Role.USER));
    var category = categories.save(new Category("下地", "seed-" + email.hashCode(), 500));
    postService.create(validForm(category.getId(), "ゲート解放用の下地投稿", "下地の教訓"), u.getEmail());
    return u;
  }

  @Test
  void anonymousUserIsRedirectedToUnlockPage() throws Exception {
    mvc.perform(get("/choices"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/experiences/unlock"));
  }

  @Test
  void loggedInUserWithoutOwnPublishedPostIsRedirectedToUnlockPage() throws Exception {
    var user = users.save(new User("choice-feed-locked@example.com", encoder.encode("password"), "未貢献者", Role.USER));
    mvc.perform(get("/choices").with(user(user.getEmail())))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/experiences/unlock"));
  }

  /** 旧・カテゴリごとの大きなカード一覧は完全に削除され、教訓そのものを並べたフィードになっている。 */
  @Test
  void oldCategoryCardListIsGoneAndLessonFeedIsShownInstead() throws Exception {
    var me = contributor("choice-feed-nocards@example.com");
    var category = categories.save(new Category("転職", "choice-feed-nocards-category", 1));
    postService.create(validForm(category.getId(), "教訓フィード確認用の体験談", "教訓フィード確認用の教訓本文"), me.getEmail());

    var body = mvc.perform(get("/choices").with(user(me.getEmail())))
        .andExpect(status().isOk())
        .andExpect(view().name("choices/list"))
        .andReturn().getResponse().getContentAsString();

    assertThat(body)
        .contains("教訓フィード確認用の教訓本文")
        .doesNotContain("経験者の知恵を見る"); // 旧デザインの大きなカードの決まり文句
  }

  @Test
  void keywordSearchMatchesLessonTextNotJustTitle() throws Exception {
    var me = contributor("choice-feed-keyword@example.com");
    var category = categories.save(new Category("転職", "choice-feed-keyword-category", 1));
    postService.create(
        validForm(category.getId(), "キーワード検索確認用のタイトル", "資格取得より過程で学び方を見つけることが大切だった"),
        me.getEmail());

    var body = mvc.perform(get("/choices").param("q", "学び方").with(user(me.getEmail())))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertThat(body).contains("資格取得より過程で学び方を見つけることが大切だった");
  }

  @Test
  void tagFilterNarrowsToMatchingPostsUsingExistingTagField() throws Exception {
    var me = contributor("choice-feed-tag@example.com");
    var category = categories.save(new Category("転職", "choice-feed-tag-category", 1));
    var withTag = new ExperiencePostForm();
    copyValidFields(withTag, category.getId(), "タグ確認用の体験談", "タグ確認用の教訓本文");
    withTag.setTagNames("転職活動");
    postService.create(withTag, me.getEmail());
    postService.create(validForm(category.getId(), "タグなし体験談", "タグなしの教訓本文"), me.getEmail());

    var body = mvc.perform(get("/choices").param("tag", "転職活動").with(user(me.getEmail())))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertThat(body).contains("タグ確認用の教訓本文").doesNotContain("タグなしの教訓本文");
  }

  @Test
  void categoryBarFilterNarrowsResultsAndMarksSelectedChipActive() throws Exception {
    var me = contributor("choice-feed-category@example.com");
    var categoryA = categories.save(new Category("転職", "choice-feed-cat-a", 1));
    var categoryB = categories.save(new Category("恋愛関連確認用", "choice-feed-cat-b", 2));
    postService.create(validForm(categoryA.getId(), "カテゴリA体験談", "カテゴリAの教訓"), me.getEmail());
    postService.create(validForm(categoryB.getId(), "カテゴリB体験談", "カテゴリBの教訓"), me.getEmail());

    var body = mvc.perform(get("/choices").param("category", "choice-feed-cat-a").with(user(me.getEmail())))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertThat(body)
        .contains("カテゴリAの教訓")
        .doesNotContain("カテゴリBの教訓")
        .contains("lesson-category-chip  is-active");
  }

  /** 存在しないカテゴリslugを指定した場合は404にはせず、0件の空状態として扱う。 */
  @Test
  void unknownCategorySlugYieldsEmptyStateInsteadOf404() throws Exception {
    var me = contributor("choice-feed-unknown-slug@example.com");

    mvc.perform(get("/choices").param("category", "no-such-slug").with(user(me.getEmail())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("条件に一致する教訓はありませんでした。")))
        .andExpect(content().string(containsString("条件をクリア")));
  }

  /**
   * 教訓必須化(ExperiencePostService.requireLesson)より前に作成された投稿を想定したケース。
   * 現在の投稿フローでは教訓なしの投稿を新規作成できないため、既存データを模してリポジトリへ
   * 直接保存し、教訓まとめのフィードから正しく除外されることを確認する。
   */
  @Test
  void postsWithoutAnyLessonTextAreExcludedFromTheFeed() throws Exception {
    var me = contributor("choice-feed-no-lesson@example.com");
    var category = categories.save(new Category("転職", "choice-feed-no-lesson-category", 1));
    var legacyPost = new ExperiencePost(me);
    legacyPost.updateContent(
        category, "教訓未記入の体験談タイトルABCXYZ", null, null, null, null,
        "状況", "悩み", "選択肢", "選んだこと", "理由", "結果", "良かったこと", "大変だったこと", "想定外だったこと",
        8, 2, false, "アドバイス");
    legacyPost.publish();
    posts.save(legacyPost);

    var body = mvc.perform(get("/choices").with(user(me.getEmail())))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain("教訓未記入の体験談タイトルABCXYZ");
  }

  @Test
  void simpleViewIsDefaultAndDetailedViewIncludesTitleAndAuthor() throws Exception {
    var me = contributor("choice-feed-view@example.com");
    var category = categories.save(new Category("転職", "choice-feed-view-category", 1));
    postService.create(validForm(category.getId(), "表示切り替え確認用タイトル", "表示切り替え確認用の教訓"), me.getEmail());

    var defaultBody = mvc.perform(get("/choices").with(user(me.getEmail())))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(defaultBody).contains("data-view=\"simple\"");

    var detailedBody = mvc.perform(get("/choices").param("view", "detailed").with(user(me.getEmail())))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(detailedBody)
        .contains("data-view=\"detailed\"")
        .contains("表示切り替え確認用タイトル");
  }

  @Test
  void paginationLinksPreserveCurrentFiltersAndPagingWorks() throws Exception {
    var me = contributor("choice-feed-paging@example.com");
    var category = categories.save(new Category("転職", "choice-feed-paging-category", 1));
    // タイトルが数字の接頭辞違いだけ(例:"確認用1"と"確認用10")だと類似度チェックに
    // 引っかかりやすいため、件数分の異なる話題語を組み合わせて内容を十分に異ならせる。
    String[] topics = {
      "転職", "独立", "進学", "留学", "資格取得", "副業開始", "引っ越し", "復職", "育児休業", "起業",
      "移住", "部署異動", "休職", "退職", "出向", "兼業解禁", "婚活", "一人暮らし", "同棲解消", "卒業",
      "就職活動", "転勤", "キャリアチェンジ", "昇進", "降格"
    };
    for (int i = 0; i < 25; i++) {
      String title = topics[i % topics.length] + "に関するページング確認用" + i;
      postService.create(validForm(category.getId(), title, "ページング確認用の教訓" + title), me.getEmail());
    }

    var firstPage = mvc.perform(
            get("/choices").param("category", "choice-feed-paging-category").with(user(me.getEmail())))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(firstPage).contains("category=choice-feed-paging-category").contains("page=1");

    mvc.perform(
            get("/choices")
                .param("category", "choice-feed-paging-category")
                .param("page", "1")
                .with(user(me.getEmail())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("2 / 2")));
  }

  private ExperiencePostForm validForm(Long categoryId, String title, String lesson) {
    var f = new ExperiencePostForm();
    copyValidFields(f, categoryId, title, lesson);
    return f;
  }

  /**
   * titleを本文の複数箇所へ織り込み、生成する投稿ごとの内容を十分に異ならせる
   * (新設のDuplicatePostDetectionServiceが「同一ユーザーの実質同一投稿」として
   * 誤検出しないようにするため。1人のcontributorが複数投稿を作るテストが多いため)。
   */
  private void copyValidFields(ExperiencePostForm f, Long categoryId, String title, String lesson) {
    f.setCategoryId(categoryId);
    f.setTitle(title);
    f.setSituationBefore("状況の詳細です。" + title);
    f.setWorries("悩みの内容です。" + title);
    f.setAlternatives("検討した選択肢です。" + title);
    f.setChoiceMade("実際に選んだことです。" + title);
    f.setReason("選んだ理由です。" + title);
    f.setOutcome("その後の結果です。" + title);
    f.setGoodThings("良かったことです。" + title);
    f.setDifficulties("大変だったことです。" + title);
    f.setUnexpectedThings("想定外だったことです。" + title);
    // lessonそのものが10文字未満の呼び出し元があるため、titleを連結して常に最小文字数を満たす
    f.setLesson(lesson + "。" + title);
    f.setSatisfaction(8);
    f.setRegret(2);
    f.setAdviceToPastSelf("アドバイスです。" + title);
  }
}
