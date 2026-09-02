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

  /**
   * 投稿フォーム改善: 最低限必要な項目(カテゴリ・タイトル・当時の状況・選択/行動・結果・満足度・後悔度)
   * だけを入力すれば投稿でき、悩み・選択肢・理由・良かったこと・大変だったこと・想定外だったこと・
   * 教訓・今ならどうするか、などの深掘り項目は空欄のままでも投稿のハードルにならないことを確認する。
   */
  @Test
  void publishingSucceedsWithOnlyTheEssentialFieldsFilled() throws Exception {
    var author = users.save(new User("minimal-post@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "minimal-post-category", 1));
    long countBefore = posts.count();

    mvc.perform(
            post("/experiences")
                .with(user(author.getEmail()))
                .with(csrf())
                .param("categoryId", String.valueOf(category.getId()))
                .param("title", "最低限の項目だけの投稿")
                .param("situationBefore", "状況のみ入力")
                .param("choiceMade", "選択のみ入力")
                .param("outcome", "結果のみ入力")
                .param("satisfaction", "7")
                .param("regret", "3"))
        .andExpect(status().is3xxRedirection());

    assertThat(posts.count()).isEqualTo(countBefore + 1);
    var saved =
        posts.findByAuthorId(author.getId()).stream()
            .filter(p -> p.getTitle().equals("最低限の項目だけの投稿"))
            .findFirst()
            .orElseThrow();
    assertThat(saved.isPublished()).isTrue();
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

    // give to getは「学び」部分のみの解放条件。自分自身が公開投稿を持つユーザーは
    // 他の公開投稿の「経験・失敗」「学び」をどちらも閲覧できる。
    mvc.perform(get("/experiences/" + postId).with(user(author.getEmail())))
        .andExpect(status().isOk())
        .andExpect(view().name("experiences/detail"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("閲覧対象の体験談")));
  }

  /**
   * 新仕様: 公開体験談の詳細ページ全体は誰でも200で閲覧できる(旧仕様のような
   * /experiences/unlockへのリダイレクトは廃止)。ただし自分自身の公開体験談を
   * 投稿していない閲覧者には、「学び」部分(教訓本文等)はサーバー側から一切渡されず、
   * 鍵付きの項目名と投稿への誘導のみが表示される。
   */
  @Test
  void userWithoutOwnContributionCanViewPublicPartButNotWisdom() throws Exception {
    var author = users.save(new User("locked-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var reader =
        users.save(new User("locked-reader@example.com", encoder.encode("password"), "未貢献者", Role.USER));
    var category = categories.save(new Category("転職", "locked-reader-category", 1));
    var postId = publishWithWisdom(author, category, "まだ貢献していない人には見せない");

    var result =
        mvc.perform(get("/experiences/" + postId).with(user(reader.getEmail())))
            .andExpect(status().isOk())
            .andExpect(view().name("experiences/detail"))
            .andExpect(model().attribute("wisdomUnlocked", false))
            .andExpect(model().attribute("wisdom", org.hamcrest.Matchers.nullValue()))
            .andReturn();
    String body = result.getResponse().getContentAsString();
    assertThat(body).contains("まだ貢献していない人には見せない", "結果の本文", "大変だったことの本文");
    assertThat(body).doesNotContain("教訓の本文", "今ならどうするかの本文");
    assertThat(body).contains("この失敗から、何を学べる？", "体験談を投稿する");
  }

  @Test
  void anonymousUserCanViewPublicPartWithSeoTagsButNotWisdom() throws Exception {
    var author = users.save(new User("anon-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "anon-view-category", 1));
    var postId = publishWithWisdom(author, category, "匿名にも公開される体験談");

    var result =
        mvc.perform(get("/experiences/" + postId))
            .andExpect(status().isOk())
            .andExpect(view().name("experiences/detail"))
            .andExpect(model().attribute("wisdomUnlocked", false))
            .andReturn();
    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .contains("匿名にも公開される体験談", "結果の本文", "大変だったことの本文", "選んだことの本文")
        .contains("name=\"description\"")
        .contains("rel=\"canonical\"")
        .contains("property=\"og:title\"")
        .doesNotContain("教訓の本文", "今ならどうするかの本文", "判断基準の本文");
  }

  @Test
  void contributorCanViewBothPublicPartAndWisdom() throws Exception {
    var author = users.save(new User("wisdom-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var contributor =
        users.save(new User("wisdom-contributor@example.com", encoder.encode("password"), "貢献者", Role.USER));
    var category = categories.save(new Category("転職", "wisdom-contributor-category", 1));
    publish(contributor, category, "貢献者自身の投稿");
    var postId = publishWithWisdom(author, category, "貢献者には全文見せる");

    var result =
        mvc.perform(get("/experiences/" + postId).with(user(contributor.getEmail())))
            .andExpect(status().isOk())
            .andExpect(model().attribute("wisdomUnlocked", true))
            .andReturn();
    String body = result.getResponse().getContentAsString();
    assertThat(body).contains("教訓の本文", "今ならどうするかの本文", "判断基準の本文");
  }

  @Test
  void metaDescriptionNeverLeaksWisdomContent() throws Exception {
    var author = users.save(new User("meta-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "meta-author-category", 1));
    var postId = publishWithWisdom(author, category, "メタディスクリプション確認用");

    var result = mvc.perform(get("/experiences/" + postId)).andExpect(status().isOk()).andReturn();
    String description = (String) result.getModelAndView().getModel().get("metaDescription");
    assertThat(description).isNotNull();
    assertThat(description).doesNotContain("教訓の本文", "今ならどうするかの本文", "判断基準の本文");
  }

  @Test
  void anonymousUserCannotPostComment() throws Exception {
    var author = users.save(new User("comment-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "comment-author-category", 1));
    var postId = publish(author, category, "コメント対象の体験談");

    mvc.perform(post("/experiences/" + postId + "/comments").with(csrf()).param("body", "コメント本文"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("**/login"));
  }

  /**
   * give to get: /experiences一覧の「簡易表示」モーダルはlist.html側でsec:authorizeによる
   * 認証ガードがなく匿名にも表示されるため、モーダル本文の学び部分をwisdomUnlockedで
   * 別途ロックしている。匿名ユーザーには公開部分(状況・選んだこと・結果・大変だったこと)
   * は表示されるが、学び本文(教訓・学んだこと)はレスポンス本文のどこにも出力されないこと、
   * 代わりにロック表示と投稿導線が出ることを確認する。
   */
  @Test
  void anonymousExperienceListNeverLeaksWisdomTextInSummaryModal() throws Exception {
    var author = users.save(new User("list-leak-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var category = categories.save(new Category("転職", "list-leak-category", 1));
    publishWithWisdom(author, category, "一覧の簡易表示確認用");

    var body =
        mvc.perform(get("/experiences"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body)
        .contains("一覧の簡易表示確認用", "選んだことの本文", "結果の本文", "大変だったことの本文")
        .contains("あなたの経験を1つ投稿すると読めます")
        .doesNotContain("教訓の本文", "学んだことの本文", "今ならどうするかの本文", "判断基準の本文");
  }

  @Test
  void contributorSeesWisdomInExperienceListSummaryModal() throws Exception {
    var author = users.save(new User("list-unlock-author@example.com", encoder.encode("password"), "投稿者", Role.USER));
    var contributor =
        users.save(new User("list-unlock-contributor@example.com", encoder.encode("password"), "貢献者", Role.USER));
    var category = categories.save(new Category("転職", "list-unlock-category", 1));
    publish(contributor, category, "貢献者自身の投稿(一覧確認用)");
    publishWithWisdom(author, category, "一覧で学びが解放される確認用");

    var body =
        mvc.perform(get("/experiences").with(user(contributor.getEmail())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).contains("学んだことの本文");
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

  /**
   * 「経験・失敗」部分と「学び」部分を、テストの本文中で見分けやすい文言で
   * 両方入力した公開投稿を作成する。学び部分がレスポンス本文へ漏れていないことを
   * 文字列一致で確認するテストで使う。
   */
  private Long publishWithWisdom(User author, Category category, String title) {
    var f = new ExperiencePostForm();
    f.setCategoryId(category.getId());
    f.setTitle(title);
    f.setSituationBefore("状況の本文");
    f.setWorries("悩みの本文");
    f.setAlternatives("選択肢の本文");
    f.setChoiceMade("選んだことの本文");
    f.setReason("理由の本文");
    f.setOutcome("結果の本文");
    f.setGoodThings("良かったことの本文");
    f.setDifficulties("大変だったことの本文");
    f.setUnexpectedThings("想定外だったことの本文");
    f.setSatisfaction(8);
    f.setRegret(2);
    f.setAdviceToPastSelf("今ならどうするかの本文");
    f.setDecisionCriteria("判断基準の本文");
    f.setLesson("教訓の本文");
    f.setLearned("学んだことの本文");
    f.setMissedRegret("後悔の本文");
    return postService.create(f, author.getEmail()).getId();
  }
}
