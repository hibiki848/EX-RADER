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
import com.exradar.form.ExperiencePostForm;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「体験談」の新規投稿(公開)・必須項目バリデーション・編集・削除・閲覧・権限制御を
 * HTTPレイヤー(MockMvc)で検証する。サービス層の詳細な挙動はExperiencePostServiceTest、
 * 下書き関連はExperienceDraftControllerTestで既にカバーしているため、ここでは
 * 公開済み投稿に対するController層の配線(redirect先・view名・権限)を中心に確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExperiencePostControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired ExperiencePostRepository posts;
  @Autowired PasswordEncoder encoder;
  @Autowired com.exradar.service.ExperiencePostService postService;

  @Test
  void missingRequiredFieldsShowsValidationErrorsAndDoesNotSave() throws Exception {
    var author = users.save(new User("missing-fields@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "missing-fields-category", 1));
    long countBefore = posts.count();

    mvc.perform(
            post("/experiences")
                .with(user(author.getEmail()))
                .with(csrf())
                .param("categoryId", String.valueOf(category.getId()))
                // タイトル・状況・悩みなど必須項目を意図的に未入力にする
                )
        .andExpect(status().isOk())
        .andExpect(view().name("experiences/form"))
        .andExpect(model().attributeHasFieldErrors("experiencePostForm", "title"))
        .andExpect(model().attributeHasFieldErrors("experiencePostForm", "situationBefore"));

    assertThat(posts.count()).isEqualTo(countBefore);
  }

  @Test
  void ownerCanEditOwnPublishedPost() throws Exception {
    var owner = users.save(new User("edit-owner@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "edit-owner-category", 1));
    var postId = publish(owner, category, "編集前のタイトル");

    mvc.perform(
            post("/experiences/" + postId)
                .with(user(owner.getEmail()))
                .with(csrf())
                .param("categoryId", String.valueOf(category.getId()))
                .param("title", "編集後のタイトル")
                .param("situationBefore", "状況")
                .param("worries", "悩み")
                .param("alternatives", "選択肢")
                .param("choiceMade", "選んだこと")
                .param("reason", "理由")
                .param("outcome", "結果")
                .param("goodThings", "良かったこと")
                .param("difficulties", "大変だったこと")
                .param("unexpectedThings", "想定外だったこと")
                .param("satisfaction", "9")
                .param("regret", "1")
                .param("adviceToPastSelf", "アドバイス"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/experiences/" + postId));

    assertThat(posts.findById(postId).orElseThrow().getTitle()).isEqualTo("編集後のタイトル");
  }

  @Test
  void otherUserCannotEditSomeonesPublishedPost() throws Exception {
    var owner = users.save(new User("edit-target@example.com", encoder.encode("password"), "所有者", Role.USER));
    var stranger = users.save(new User("edit-stranger@example.com", encoder.encode("password"), "他人", Role.USER));
    var category = categories.save(new Category("転職", "edit-stranger-category", 1));
    var postId = publish(owner, category, "他人には編集させない");

    mvc.perform(
            post("/experiences/" + postId)
                .with(user(stranger.getEmail()))
                .with(csrf())
                .param("categoryId", String.valueOf(category.getId()))
                .param("title", "改ざんされたタイトル")
                .param("situationBefore", "状況")
                .param("worries", "悩み")
                .param("alternatives", "選択肢")
                .param("choiceMade", "選んだこと")
                .param("reason", "理由")
                .param("outcome", "結果")
                .param("goodThings", "良かったこと")
                .param("difficulties", "大変だったこと")
                .param("unexpectedThings", "想定外だったこと")
                .param("satisfaction", "9")
                .param("regret", "1")
                .param("adviceToPastSelf", "アドバイス"))
        .andExpect(status().isForbidden());

    assertThat(posts.findById(postId).orElseThrow().getTitle()).isEqualTo("他人には編集させない");
  }

  @Test
  void ownerCanDeleteOwnPost() throws Exception {
    var owner = users.save(new User("delete-owner@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "delete-owner-category", 1));
    var postId = publish(owner, category, "削除される投稿");

    mvc.perform(post("/experiences/" + postId + "/delete").with(user(owner.getEmail())).with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/mypage"));

    assertThat(posts.findById(postId)).isEmpty();
  }

  @Test
  void otherUserCannotDeleteSomeonesPost() throws Exception {
    var owner = users.save(new User("delete-target@example.com", encoder.encode("password"), "所有者", Role.USER));
    var stranger =
        users.save(new User("delete-stranger@example.com", encoder.encode("password"), "他人", Role.USER));
    var category = categories.save(new Category("転職", "delete-stranger-category", 1));
    var postId = publish(owner, category, "他人には削除させない");

    mvc.perform(post("/experiences/" + postId + "/delete").with(user(stranger.getEmail())).with(csrf()))
        .andExpect(status().isForbidden());

    assertThat(posts.findById(postId)).isPresent();
  }

  @Test
  void unlockedUserCanViewPublishedPost() throws Exception {
    var author = users.save(new User("view-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "view-author-category", 1));
    var postId = publish(author, category, "閲覧対象の体験談");

    // 「give to get」により、自分自身が公開投稿を持つユーザーは他の公開投稿を閲覧できる
    mvc.perform(get("/experiences/" + postId).with(user(author.getEmail())))
        .andExpect(status().isOk())
        .andExpect(view().name("experiences/detail"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("閲覧対象の体験談")));
  }

  @Test
  void userWithoutOwnContributionIsRedirectedToUnlockInsteadOfViewing() throws Exception {
    var author = users.save(new User("locked-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var reader =
        users.save(new User("locked-reader@example.com", encoder.encode("password"), "未貢献者", Role.USER));
    var category = categories.save(new Category("転職", "locked-reader-category", 1));
    var postId = publish(author, category, "まだ貢献していない人には見せない");

    mvc.perform(get("/experiences/" + postId).with(user(reader.getEmail())))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/experiences/unlock"));
  }

  @Test
  void anonymousUserCannotSubmitNewExperiencePost() throws Exception {
    var category = categories.save(new Category("転職", "anonymous-post-category", 1));
    long countBefore = posts.count();

    // 未ログイン状態では、たとえ入力内容が完全に有効な投稿であっても
    // Controllerへ到達する前にSpring Securityでブロックされ、ログインへ誘導されること
    mvc.perform(
            post("/experiences")
                .with(csrf())
                .param("categoryId", String.valueOf(category.getId()))
                .param("title", "未ログインでの投稿")
                .param("situationBefore", "状況")
                .param("worries", "悩み")
                .param("alternatives", "選択肢")
                .param("choiceMade", "選んだこと")
                .param("reason", "理由")
                .param("outcome", "結果")
                .param("goodThings", "良かったこと")
                .param("difficulties", "大変だったこと")
                .param("unexpectedThings", "想定外だったこと")
                .param("satisfaction", "8")
                .param("regret", "2")
                .param("adviceToPastSelf", "アドバイス"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("**/login"));

    assertThat(posts.count()).isEqualTo(countBefore);
  }

  private Long publish(User author, Category category, String title) {
    var f = new ExperiencePostForm();
    f.setCategoryId(category.getId());
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
    f.setSatisfaction(8);
    f.setRegret(2);
    f.setAdviceToPastSelf("アドバイス");
    return postService.create(f, author.getEmail()).getId();
  }
}
