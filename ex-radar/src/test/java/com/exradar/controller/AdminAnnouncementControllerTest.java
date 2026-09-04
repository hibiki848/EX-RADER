package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.AdminAnnouncementRecipientRepository;
import com.exradar.repository.AdminAnnouncementRepository;
import com.exradar.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 管理者からの「ログイン時お知らせ」画面(/admin/announcements/**)。アクセス制御(ADMIN限定・CSRF)、
 * 「GETだけでは絶対に作成が起きない」こと、0人配信の拒否、作成・編集・公開/非公開・履歴表示を確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminAnnouncementControllerTest {
  private static final DateTimeFormatter DATETIME_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired AdminAnnouncementRepository announcements;
  @Autowired AdminAnnouncementRecipientRepository recipients;
  @Autowired PasswordEncoder encoder;
  @Autowired PlatformTransactionManager transactionManager;

  private User admin(String email) {
    return users.save(new User(email, encoder.encode("password123"), "運営", Role.ADMIN));
  }

  private User regularUser(String email) {
    return users.save(new User(email, encoder.encode("password123"), "一般ユーザー", Role.USER));
  }

  private String past() {
    return LocalDateTime.now().minusDays(1).format(DATETIME_LOCAL);
  }

  private Long announcementIdFromRedirect(org.springframework.test.web.servlet.MvcResult result) {
    var location = result.getResponse().getRedirectedUrl();
    return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
  }

  @Test
  void regularUserCannotOpenComposeScreenOrHistory() throws Exception {
    mvc.perform(get("/admin/announcements").with(user("user@example.com").roles("USER")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/admin/announcements/new").with(user("user@example.com").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void regularUserCannotCreate() throws Exception {
    var target = regularUser("create-forbidden-target@example.com");

    mvc.perform(
            post("/admin/announcements")
                .with(user("attacker@example.com").roles("USER"))
                .with(csrf())
                .param("title", "不正作成テスト")
                .param("body", "本文")
                .param("startsAt", past())
                .param("targetMode", "EXPLICIT")
                .param("selectedUserId", target.getId().toString()))
        .andExpect(status().isForbidden());
    assertThat(announcements.count()).isZero();
  }

  @Test
  void anonymousCannotOpenComposeScreen() throws Exception {
    mvc.perform(get("/admin/announcements/new")).andExpect(status().is3xxRedirection());
  }

  /** GETは検索条件の対象人数プレビューを表示するだけで、いかなる場合もお知らせを作成しない。 */
  @Test
  void openingComposeScreenWithGetNeverCreatesAnAnnouncement() throws Exception {
    var admin = admin("compose-get-admin@example.com");
    var target = regularUser("compose-get-target@example.com");

    mvc.perform(
            get("/admin/announcements/new")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .param("selectedUserId", target.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/announcements/form"))
        .andExpect(model().attribute("targetMode", "EXPLICIT"))
        .andExpect(model().attribute("targetCount", 1));
    assertThat(announcements.count()).isZero();
  }

  @Test
  void createWithoutCsrfIsRejectedAndCreatesNothing() throws Exception {
    var admin = admin("csrf-admin@example.com");
    var target = regularUser("csrf-target@example.com");

    mvc.perform(
            post("/admin/announcements")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .param("title", "CSRF無しテスト")
                .param("body", "本文")
                .param("startsAt", past())
                .param("targetMode", "EXPLICIT")
                .param("selectedUserId", target.getId().toString()))
        .andExpect(status().isForbidden());
    assertThat(announcements.count()).isZero();
  }

  @Test
  void createWithZeroRecipientsIsRejected() throws Exception {
    var admin = admin("zero-admin@example.com");

    mvc.perform(
            post("/admin/announcements")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("title", "0人配信テスト")
                .param("body", "本文")
                .param("startsAt", past())
                .param("targetMode", "EXPLICIT"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/announcements/form"))
        .andExpect(model().attribute("targetError", "配信先が0人のため作成できません"));
    assertThat(announcements.count()).isZero();
  }

  @Test
  void adminCanCreateForASingleExplicitlySelectedUser() throws Exception {
    var admin = admin("single-admin@example.com");
    var target = regularUser("single-target@example.com");

    var result =
        mvc.perform(
                post("/admin/announcements")
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .with(csrf())
                    .param("title", "個別配信テスト")
                    .param("body", "本文です")
                    .param("startsAt", past())
                    .param("targetMode", "EXPLICIT")
                    .param("selectedUserId", target.getId().toString()))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    var id = announcementIdFromRedirect(result);
    assertThat(recipients.countByAnnouncementId(id)).isEqualTo(1);
    assertThat(announcements.findById(id).orElseThrow().isPublished()).isFalse();
  }

  @Test
  void adminCanCreateForMultipleExplicitlySelectedUsers() throws Exception {
    var admin = admin("multi-admin@example.com");
    var a = regularUser("multi-target-a@example.com");
    var b = regularUser("multi-target-b@example.com");

    var result =
        mvc.perform(
                post("/admin/announcements")
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .with(csrf())
                    .param("title", "複数配信テスト")
                    .param("body", "本文です")
                    .param("startsAt", past())
                    .param("targetMode", "EXPLICIT")
                    .param("selectedUserId", a.getId().toString(), b.getId().toString()))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    var id = announcementIdFromRedirect(result);
    assertThat(recipients.countByAnnouncementId(id)).isEqualTo(2);
  }

  /** 検索条件(AdminUserSearchCriteria)に一致する全員への配信は、AdminUserSearchServiceをそのまま再利用する。 */
  @Test
  void adminCanCreateForAllUsersMatchingSearchCriteria() throws Exception {
    var admin = admin("criteria-admin@example.com");
    regularUser("criteria-match-a@example.com");
    regularUser("criteria-match-b@example.com");
    regularUser("other-user@example.com");

    var result =
        mvc.perform(
                post("/admin/announcements")
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .with(csrf())
                    .param("title", "検索条件配信テスト")
                    .param("body", "本文です")
                    .param("startsAt", past())
                    .param("targetMode", "CRITERIA")
                    .param("email", "criteria-match-"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    var id = announcementIdFromRedirect(result);
    assertThat(recipients.countByAnnouncementId(id)).isEqualTo(2);
  }

  @Test
  void adminCanPublishAndUnpublish() throws Exception {
    var admin = admin("publish-admin@example.com");
    var target = regularUser("publish-target@example.com");

    var result =
        mvc.perform(
                post("/admin/announcements")
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .with(csrf())
                    .param("title", "公開切替テスト")
                    .param("body", "本文です")
                    .param("startsAt", past())
                    .param("targetMode", "EXPLICIT")
                    .param("selectedUserId", target.getId().toString()))
            .andReturn();
    var id = announcementIdFromRedirect(result);
    assertThat(announcements.findById(id).orElseThrow().isPublished()).isFalse();

    mvc.perform(post("/admin/announcements/{id}/publish", id).with(user(admin.getEmail()).roles("ADMIN")).with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertThat(announcements.findById(id).orElseThrow().isPublished()).isTrue();

    mvc.perform(post("/admin/announcements/{id}/unpublish", id).with(user(admin.getEmail()).roles("ADMIN")).with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertThat(announcements.findById(id).orElseThrow().isPublished()).isFalse();
  }

  @Test
  void nonAdminCannotPublish() throws Exception {
    var admin = admin("publish-security-admin@example.com");
    var target = regularUser("publish-security-target@example.com");
    var result =
        mvc.perform(
            post("/admin/announcements")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("title", "公開権限テスト")
                .param("body", "本文です")
                .param("startsAt", past())
                .param("targetMode", "EXPLICIT")
                .param("selectedUserId", target.getId().toString()));
    var id = announcementIdFromRedirect(result.andReturn());

    mvc.perform(post("/admin/announcements/{id}/publish", id).with(user("attacker@example.com").roles("USER")).with(csrf()))
        .andExpect(status().isForbidden());
    assertThat(announcements.findById(id).orElseThrow().isPublished()).isFalse();
  }

  @Test
  void adminCanEditContentButTargetSetIsUnaffected() throws Exception {
    var admin = admin("edit-admin@example.com");
    var target = regularUser("edit-target@example.com");
    var result =
        mvc.perform(
            post("/admin/announcements")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("title", "編集前タイトル")
                .param("body", "編集前本文")
                .param("startsAt", past())
                .param("targetMode", "EXPLICIT")
                .param("selectedUserId", target.getId().toString()));
    var id = announcementIdFromRedirect(result.andReturn());

    mvc.perform(
            post("/admin/announcements/{id}/edit", id)
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("title", "編集後タイトル")
                .param("body", "編集後本文")
                .param("startsAt", past())
                .param("priority", "3"))
        .andExpect(status().is3xxRedirection());

    var updated = announcements.findById(id).orElseThrow();
    assertThat(updated.getTitle()).isEqualTo("編集後タイトル");
    assertThat(updated.getPriority()).isEqualTo(3);
    assertThat(recipients.countByAnnouncementId(id)).isEqualTo(1);
  }

  @Test
  void adminHistoryShowsDeliveredAndDisplayedCounts() throws Exception {
    var admin = admin("history-admin@example.com");
    var target = regularUser("history-target@example.com");
    var result =
        mvc.perform(
            post("/admin/announcements")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("title", "履歴確認用お知らせ")
                .param("body", "本文です")
                .param("startsAt", past())
                .param("targetMode", "EXPLICIT")
                .param("selectedUserId", target.getId().toString()));
    var id = announcementIdFromRedirect(result.andReturn());

    mvc.perform(get("/admin/announcements").with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/announcements/list"));

    mvc.perform(get("/admin/announcements/{id}", id).with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/announcements/detail"));

    assertThat(recipients.countByAnnouncementId(id)).isEqualTo(1);
    assertThat(recipients.countByAnnouncementIdAndFirstDisplayedAtIsNotNull(id)).isZero();
  }

  /**
   * spring.jpa.open-in-view=false(application.yml)のため、本番では1リクエスト=1トランザクション。
   * このテストクラスは@Transactionalだが、その仕組み(TransactionalTestExecutionListener)は
   * テストメソッド全体を1つの外側トランザクション(1つのHibernateセッション)でくるむため、
   * その中でMockMvcのリクエストを何回呼んでも同じセッションが使い回されてしまい、
   * announcement.createdByAdmin(LAZY)のようなLazyInitializationExceptionが本番では起きるのに
   * テストでは再現しない、という事故が起きる(運営メッセージ機能で実際にこの種の不具合を
   * 手動ブラウザ確認で検出・修正した)。このテストメソッドだけPropagation.NOT_SUPPORTEDで
   * 外側トランザクションを無効化し、作成(POST)と詳細表示(GET)がそれぞれ独立した
   * トランザクション/セッションで処理される本番のリクエスト境界を再現する。
   * ロールバックされないため、finallyで必ず後片付けする。
   */
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void detailScreenRendersCreatedByAdminAcrossASeparateTransactionFromCreation() throws Exception {
    var admin = admin("cross-tx-admin@example.com");
    var target = regularUser("cross-tx-target@example.com");
    Long id = null;
    try {
      var result =
          mvc.perform(
                  post("/admin/announcements")
                      .with(user(admin.getEmail()).roles("ADMIN"))
                      .with(csrf())
                      .param("title", "トランザクション境界確認用お知らせ")
                      .param("body", "本文です")
                      .param("startsAt", past())
                      .param("targetMode", "EXPLICIT")
                      .param("selectedUserId", target.getId().toString()))
              .andExpect(status().is3xxRedirection())
              .andReturn();
      id = announcementIdFromRedirect(result);

      mvc.perform(get("/admin/announcements/{id}", id).with(user(admin.getEmail()).roles("ADMIN")))
          .andExpect(status().isOk())
          .andExpect(view().name("admin/announcements/detail"))
          .andExpect(content().string(org.hamcrest.Matchers.containsString(admin.getDisplayName() + "さんが作成")));
    } finally {
      Long finalId = id;
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                recipients.deleteByUserId(target.getId());
                if (finalId != null) announcements.deleteById(finalId);
                users.delete(target);
                users.delete(admin);
              });
    }
  }
}
