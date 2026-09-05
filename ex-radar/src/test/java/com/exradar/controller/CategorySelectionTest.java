package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Category;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * カテゴリ機能の一連の動作確認(投稿→DB保存→表示→検索)。
 * DevDataInitializer(devプロファイル限定)ではなく、Flywayでシードされた実際のカテゴリ
 * (V7__seed_categories.sql)を使って検証する。これにより、本番相当の環境でも
 * 新規投稿画面・検索画面でカテゴリが選択できることを保証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategorySelectionTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired ExperiencePostRepository posts;

  @Test
  void flywaySeedsAllExistingCategoriesPlusLove() {
    var active = categories.findByActiveTrueOrderByDisplayOrder();
    var names = active.stream().map(Category::getName).toList();

    // 既存カテゴリが維持されていること
    assertThat(names)
        .contains(
            "勉強", "高校進学", "大学進学", "専門学校", "高卒就職", "大学中退", "就職", "転職",
            "異業種転職", "公務員", "資格取得", "上京", "地元就職", "地元へ戻る", "フリーランス");
    // 新規カテゴリが追加されていること
    assertThat(names).contains("恋愛");
    assertThat(categories.findBySlug("love")).isPresent();
  }

  /** V18で追加した4カテゴリ(仕事・人間関係・お金・その他)が既存カテゴリを維持したまま追加されていること。 */
  @Test
  void flywaySeedsFourAdditionalCategoriesWithoutRemovingExisting() {
    var active = categories.findByActiveTrueOrderByDisplayOrder();
    var names = active.stream().map(Category::getName).toList();

    assertThat(names)
        .contains(
            "勉強", "高校進学", "大学進学", "専門学校", "高卒就職", "大学中退", "就職", "転職",
            "異業種転職", "公務員", "資格取得", "上京", "地元就職", "地元へ戻る", "フリーランス", "恋愛");
    assertThat(names).contains("仕事", "人間関係", "お金", "その他");
    assertThat(categories.findBySlug("work")).isPresent();
    assertThat(categories.findBySlug("relationships")).isPresent();
    assertThat(categories.findBySlug("money")).isPresent();
    assertThat(categories.findBySlug("other")).isPresent();
  }

  @Test
  void newExperienceFormOffersRealCategoryOptionsIncludingLove() throws Exception {
    users.save(new User("category-form-user@example.com", "encoded", "カテゴリ確認", Role.USER));

    mvc.perform(get("/experiences/new").with(user("category-form-user@example.com")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("恋愛")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("転職")));
  }

  @Test
  void searchPageOffersRealCategoryOptionsIncludingLove() throws Exception {
    mvc.perform(get("/experiences"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("恋愛")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("すべて")));
  }

  /** 新カテゴリ「仕事」で投稿でき、DBに正しく保存され、一覧・検索・教訓まとめのいずれでも絞り込める。 */
  @Test
  void submittingWorkCategoryPersistsAndIsFilterableEverywhere() throws Exception {
    var author =
        users.save(new User("category-work-user@example.com", "encoded", "投稿者", Role.USER));
    Long workCategoryId = categories.findBySlug("work").orElseThrow().getId();

    mvc.perform(
            post("/experiences")
                .with(user(author.getEmail()))
                .with(csrf())
                .param("categoryId", String.valueOf(workCategoryId))
                .param("title", "仕事に関する体験談")
                .param("situationBefore", "状況")
                .param("worries", "悩み")
                .param("alternatives", "選択肢")
                .param("choiceMade", "選んだこと")
                .param("reason", "理由")
                .param("outcome", "結果")
                .param("goodThings", "良かったこと")
                .param("difficulties", "大変だったこと")
                .param("unexpectedThings", "想定外だったこと")
                .param("learned", "学んだこと")
                .param("lesson", "教訓の本文をここに記載します")
                .param("satisfaction", "8")
                .param("regret", "2")
                .param("adviceToPastSelf", "アドバイス")
                .param("published", "true"))
        .andExpect(status().is3xxRedirection());

    var created =
        posts.findByAuthorId(author.getId()).stream()
            .filter(p -> p.getTitle().equals("仕事に関する体験談"))
            .findFirst()
            .orElseThrow();
    assertThat(created.getCategory().getName()).isEqualTo("仕事");

    // 検索画面(/experiences)で絞り込める
    mvc.perform(get("/experiences").param("categoryId", String.valueOf(workCategoryId)))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("仕事に関する体験談")));

    // 教訓まとめ(/choices)でも同じカテゴリで絞り込める(教訓を読める=投稿者自身なのでゲートを満たす)
    mvc.perform(
            get("/choices")
                .param("category", "work")
                .with(user(author.getEmail())))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("学んだこと")));
  }

  @Test
  void submittingLoveCategoryPersistsAndFiltersCorrectly() throws Exception {
    User author =
        users.save(new User("category-submit-user@example.com", "encoded", "投稿者", Role.USER));
    Long loveCategoryId = categories.findBySlug("love").orElseThrow().getId();
    Long careerCategoryId = categories.findBySlug("career-change").orElseThrow().getId();

    mvc.perform(
            post("/experiences")
                .with(user(author.getEmail()))
                .with(csrf())
                .param("categoryId", String.valueOf(loveCategoryId))
                .param("title", "恋愛に関する体験談")
                .param("situationBefore", "状況")
                .param("worries", "悩み")
                .param("alternatives", "選択肢")
                .param("choiceMade", "選んだこと")
                .param("reason", "理由")
                .param("outcome", "結果")
                .param("goodThings", "良かったこと")
                .param("difficulties", "大変だったこと")
                .param("unexpectedThings", "想定外だったこと")
                .param("learned", "学んだこと")
                .param("lesson", "教訓の本文をここに記載します")
                .param("satisfaction", "8")
                .param("regret", "2")
                .param("adviceToPastSelf", "アドバイス")
                .param("published", "true"))
        .andExpect(status().is3xxRedirection());

    // DBへ正しいカテゴリで保存されていること(Controller→Service→Repository→Entity→DB)
    var created =
        posts.findByAuthorId(author.getId()).stream()
            .filter(p -> p.getTitle().equals("恋愛に関する体験談"))
            .findFirst()
            .orElseThrow();
    assertThat(created.getCategory().getId()).isEqualTo(loveCategoryId);
    assertThat(created.getCategory().getName()).isEqualTo("恋愛");

    // 検索画面でカテゴリ絞り込みが正しく機能すること
    mvc.perform(get("/experiences").param("categoryId", String.valueOf(loveCategoryId)))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("恋愛に関する体験談")));

    // 別カテゴリで絞り込むとヒットしないこと(絞り込みが正しく機能している証拠)
    mvc.perform(get("/experiences").param("categoryId", String.valueOf(careerCategoryId)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("恋愛に関する体験談"))));

    // カテゴリ未指定(「すべて」)では従来どおり表示されること
    mvc.perform(get("/experiences"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("恋愛に関する体験談")));
  }
}
