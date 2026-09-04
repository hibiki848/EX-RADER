package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Category;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.form.ExperiencePostForm;
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

  @Test
  void postsWithoutAnyLessonTextAreExcludedFromTheFeed() throws Exception {
    var me = contributor("choice-feed-no-lesson@example.com");
    var category = categories.save(new Category("転職", "choice-feed-no-lesson-category", 1));
    var f = validForm(category.getId(), "教訓未記入の体験談タイトルABCXYZ", null);
    postService.create(f, me.getEmail());

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
    for (int i = 0; i < 25; i++) {
      postService.create(validForm(category.getId(), "ページング確認用" + i, "ページング確認用の教訓" + i), me.getEmail());
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

  private void copyValidFields(ExperiencePostForm f, Long categoryId, String title, String lesson) {
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
    f.setLearned(lesson);
    f.setSatisfaction(8);
    f.setRegret(2);
    f.setAdviceToPastSelf("アドバイス");
  }
}
