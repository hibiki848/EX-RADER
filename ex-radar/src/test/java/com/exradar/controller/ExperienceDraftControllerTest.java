package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.*;
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
 * 「経験談の新規投稿」の投稿フロー、下書き保存、自動保存エンドポイントの一連の動作確認。
 * カテゴリ・満足度・後悔度を一切入力しない「ほぼ空の下書き保存」が通ること、
 * 下書き→公開への遷移が同一レコードを更新すること、他人の下書きへは403になること、
 * 公開済み投稿に対する遅延自動保存がDRAFTへ戻さないことを検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExperienceDraftControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired ExperiencePostRepository posts;

  @Test
  void publishingNewExperienceSucceedsAndIsPublished() throws Exception {
    var author = users.save(new User("publish-flow@example.com", "encoded", "投稿者", Role.USER));
    var category = categories.findByActiveTrueOrderByDisplayOrder().getFirst();

    mvc.perform(
            post("/experiences")
                .with(user(author.getEmail()))
                .with(csrf())
                .param("categoryId", String.valueOf(category.getId()))
                .param("title", "公開フローの確認")
                .param("situationBefore", "状況")
                .param("worries", "悩み")
                .param("alternatives", "選択肢")
                .param("choiceMade", "選んだこと")
                .param("reason", "理由")
                .param("outcome", "結果")
                .param("goodThings", "良かったこと")
                .param("difficulties", "大変だったこと")
                .param("unexpectedThings", "想定外だったこと")
                .param("lesson", "この経験から得た教訓の本文です")
                .param("satisfaction", "8")
                .param("regret", "2")
                .param("adviceToPastSelf", "アドバイス"))
        .andExpect(status().is3xxRedirection());

    var saved =
        posts.findByAuthorId(author.getId()).stream()
            .filter(p -> p.getTitle().equals("公開フローの確認"))
            .findFirst()
            .orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(PostStatus.PUBLISHED);
  }

  @Test
  void draftSaveAllowsNearEmptyFormAndNeverAppearsPublicly() throws Exception {
    var author = users.save(new User("draft-flow@example.com", "encoded", "投稿者", Role.USER));

    var result =
        mvc.perform(
                post("/experiences/draft")
                    .with(user(author.getEmail()))
                    .with(csrf())
                    .param("title", "下書きタイトルのみ"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    var redirect = result.getResponse().getRedirectedUrl();
    assertThat(redirect).matches("/experiences/\\d+/edit");

    var saved =
        posts.findByAuthorId(author.getId()).stream()
            .filter(p -> p.getTitle().equals("下書きタイトルのみ"))
            .findFirst()
            .orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(PostStatus.DRAFT);
    assertThat(saved.getCategory()).isNull();

    // 下書きは一覧・検索など公開系の画面には一切出てこない
    mvc.perform(get("/experiences"))
        .andExpect(status().isOk())
        .andExpect(
            content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("下書きタイトルのみ"))));
  }

  @Test
  void editingDraftAndSavingDraftAgainUpdatesSameRecord() throws Exception {
    var author = users.save(new User("draft-edit-flow@example.com", "encoded", "投稿者", Role.USER));
    var created =
        mvc.perform(
                post("/experiences/draft")
                    .with(user(author.getEmail()))
                    .with(csrf())
                    .param("title", "編集前の下書き"))
            .andReturn();
    Long id = extractId(created.getResponse().getRedirectedUrl());
    long countBefore = posts.count();

    mvc.perform(
            post("/experiences/" + id + "/draft")
                .with(user(author.getEmail()))
                .with(csrf())
                .param("title", "編集後の下書き"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/experiences/" + id + "/edit"));

    assertThat(posts.count()).isEqualTo(countBefore);
    var saved = posts.findById(id).orElseThrow();
    assertThat(saved.getTitle()).isEqualTo("編集後の下書き");
    assertThat(saved.getStatus()).isEqualTo(PostStatus.DRAFT);
  }

  @Test
  void publishingDraftTransitionsSameRecordToPublished() throws Exception {
    var author = users.save(new User("draft-publish-flow@example.com", "encoded", "投稿者", Role.USER));
    var category = categories.findByActiveTrueOrderByDisplayOrder().getFirst();
    var created =
        mvc.perform(
                post("/experiences/draft")
                    .with(user(author.getEmail()))
                    .with(csrf())
                    .param("title", "公開待ちの下書き"))
            .andReturn();
    Long id = extractId(created.getResponse().getRedirectedUrl());
    long countBefore = posts.count();

    mvc.perform(
            post("/experiences/" + id)
                .with(user(author.getEmail()))
                .with(csrf())
                .param("categoryId", String.valueOf(category.getId()))
                .param("title", "公開された下書き")
                .param("situationBefore", "状況")
                .param("worries", "悩み")
                .param("alternatives", "選択肢")
                .param("choiceMade", "選んだこと")
                .param("reason", "理由")
                .param("outcome", "結果")
                .param("goodThings", "良かったこと")
                .param("difficulties", "大変だったこと")
                .param("unexpectedThings", "想定外だったこと")
                .param("lesson", "公開された下書きの教訓本文です")
                .param("satisfaction", "8")
                .param("regret", "2")
                .param("adviceToPastSelf", "アドバイス"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/experiences/" + id));

    assertThat(posts.count()).isEqualTo(countBefore);
    var saved = posts.findById(id).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    assertThat(saved.getTitle()).isEqualTo("公開された下書き");
  }

  @Test
  void otherUserCannotViewEditOrAutosaveSomeoneElsesDraft() throws Exception {
    var owner = users.save(new User("draft-owner@example.com", "encoded", "所有者", Role.USER));
    var stranger = users.save(new User("draft-stranger@example.com", "encoded", "他人", Role.USER));
    var created =
        mvc.perform(
                post("/experiences/draft")
                    .with(user(owner.getEmail()))
                    .with(csrf())
                    .param("title", "他人には見せない下書き"))
            .andReturn();
    Long id = extractId(created.getResponse().getRedirectedUrl());

    mvc.perform(get("/experiences/" + id + "/edit").with(user(stranger.getEmail())))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/experiences/" + id + "/draft")
                .with(user(stranger.getEmail()))
                .with(csrf())
                .param("title", "改ざん"))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/experiences/" + id + "/draft/autosave")
                .with(user(stranger.getEmail()))
                .with(csrf())
                .param("title", "改ざん"))
        .andExpect(status().isForbidden());

    assertThat(posts.findById(id).orElseThrow().getTitle()).isEqualTo("他人には見せない下書き");
  }

  @Test
  void autosaveNeverRevertsAnAlreadyPublishedPostBackToDraft() throws Exception {
    var author = users.save(new User("late-autosave@example.com", "encoded", "投稿者", Role.USER));
    var category = categories.findByActiveTrueOrderByDisplayOrder().getFirst();
    var post = new ExperiencePost(author);
    post.updateContent(
        category, "公開済み投稿", 25, "会社員", "30代", 5, "状況", "悩み", "選択肢", "選択", "理由", "結果", "良かった", "大変",
        "想定外", 8, 2, true, "助言");
    post.publish();
    post = posts.save(post);

    mvc.perform(
            post("/experiences/" + post.getId() + "/draft/autosave")
                .with(user(author.getEmail()))
                .with(csrf())
                .param("title", "遅延到着の自動保存"))
        .andExpect(status().isForbidden());

    var stillPublished = posts.findById(post.getId()).orElseThrow();
    assertThat(stillPublished.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    assertThat(stillPublished.getTitle()).isEqualTo("公開済み投稿");
  }

  @Test
  void firstAutosaveCreatesExactlyOneDraftAndSecondAutosaveReusesIt() throws Exception {
    var author = users.save(new User("autosave-flow@example.com", "encoded", "投稿者", Role.USER));
    long countBefore = posts.count();

    var first =
        mvc.perform(
                post("/experiences/draft/autosave")
                    .with(user(author.getEmail()))
                    .with(csrf())
                    .param("title", "自動保存1回目"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(posts.count()).isEqualTo(countBefore + 1);
    Long id =
        Long.valueOf(
            first
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":(\\d+).*", "$1"));

    mvc.perform(
            post("/experiences/" + id + "/draft/autosave")
                .with(user(author.getEmail()))
                .with(csrf())
                .param("title", "自動保存2回目"))
        .andExpect(status().isOk());

    assertThat(posts.count()).isEqualTo(countBefore + 1);
    var saved = posts.findById(id).orElseThrow();
    assertThat(saved.getTitle()).isEqualTo("自動保存2回目");
    assertThat(saved.getStatus()).isEqualTo(PostStatus.DRAFT);
  }

  private Long extractId(String redirectUrl) {
    return Long.valueOf(redirectUrl.replaceAll("\\D+", ""));
  }
}
