package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.AdminMessageRecipientRepository;
import com.exradar.repository.AdminMessageRepository;
import com.exradar.repository.UserRepository;
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
 * 管理者からのメッセージ配信画面(/admin/messages/**)。アクセス制御(ADMIN限定・CSRF)、
 * 「GETだけでは絶対に送信が起きない」こと、0人送信の拒否、実際の配信・配信履歴表示を確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminMessagingControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired AdminMessageRepository messages;
  @Autowired AdminMessageRecipientRepository recipients;
  @Autowired PasswordEncoder encoder;
  @Autowired PlatformTransactionManager transactionManager;

  private User admin(String email) {
    return users.save(new User(email, encoder.encode("password123"), "運営", Role.ADMIN));
  }

  private User regularUser(String email) {
    return users.save(new User(email, encoder.encode("password123"), "一般ユーザー", Role.USER));
  }

  @Test
  void regularUserCannotOpenComposeScreenOrHistory() throws Exception {
    mvc.perform(get("/admin/messages").with(user("user@example.com").roles("USER")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/admin/messages/new").with(user("user@example.com").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void regularUserCannotSend() throws Exception {
    var target = regularUser("send-forbidden-target@example.com");

    mvc.perform(
            post("/admin/messages")
                .with(user("attacker@example.com").roles("USER"))
                .with(csrf())
                .param("title", "不正送信テスト")
                .param("body", "本文")
                .param("targetMode", "EXPLICIT")
                .param("selectedUserId", target.getId().toString()))
        .andExpect(status().isForbidden());
    assertThat(messages.count()).isZero();
  }

  @Test
  void anonymousCannotOpenComposeScreen() throws Exception {
    mvc.perform(get("/admin/messages/new")).andExpect(status().is3xxRedirection());
  }

  /** GETは検索条件の対象人数プレビューを表示するだけで、いかなる場合もメッセージを作成しない。 */
  @Test
  void openingComposeScreenWithGetNeverCreatesAMessage() throws Exception {
    var admin = admin("compose-get-admin@example.com");
    var target = regularUser("compose-get-target@example.com");

    mvc.perform(
            get("/admin/messages/new")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .param("selectedUserId", target.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/messages/form"))
        .andExpect(model().attribute("targetMode", "EXPLICIT"))
        .andExpect(model().attribute("targetCount", 1));
    assertThat(messages.count()).isZero();
  }

  @Test
  void sendWithoutCsrfIsRejectedAndCreatesNoMessage() throws Exception {
    var admin = admin("csrf-admin@example.com");
    var target = regularUser("csrf-target@example.com");

    mvc.perform(
            post("/admin/messages")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .param("title", "CSRF無しテスト")
                .param("body", "本文")
                .param("targetMode", "EXPLICIT")
                .param("selectedUserId", target.getId().toString()))
        .andExpect(status().isForbidden());
    assertThat(messages.count()).isZero();
  }

  @Test
  void sendWithZeroRecipientsIsRejected() throws Exception {
    var admin = admin("zero-admin@example.com");

    mvc.perform(
            post("/admin/messages")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("title", "0人送信テスト")
                .param("body", "本文")
                .param("targetMode", "EXPLICIT"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/messages/form"))
        .andExpect(model().attribute("targetError", "送信先が0人のため送信できません"));
    assertThat(messages.count()).isZero();
  }

  /** リダイレクト先URL(/admin/messages/{id})からメッセージIDを取り出す。DBの他のデータに依存しない。 */
  private Long messageIdFromRedirect(org.springframework.test.web.servlet.MvcResult result) {
    var location = result.getResponse().getRedirectedUrl();
    return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
  }

  @Test
  void adminCanSendToASingleExplicitlySelectedUser() throws Exception {
    var admin = admin("single-admin@example.com");
    var target = regularUser("single-target@example.com");

    var result =
        mvc.perform(
                post("/admin/messages")
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .with(csrf())
                    .param("title", "個別配信テスト")
                    .param("body", "本文です")
                    .param("targetMode", "EXPLICIT")
                    .param("selectedUserId", target.getId().toString()))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    var messageId = messageIdFromRedirect(result);
    assertThat(recipients.countByMessageId(messageId)).isEqualTo(1);
  }

  @Test
  void adminCanSendToMultipleExplicitlySelectedUsers() throws Exception {
    var admin = admin("multi-admin@example.com");
    var a = regularUser("multi-target-a@example.com");
    var b = regularUser("multi-target-b@example.com");

    var result =
        mvc.perform(
                post("/admin/messages")
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .with(csrf())
                    .param("title", "複数配信テスト")
                    .param("body", "本文です")
                    .param("targetMode", "EXPLICIT")
                    .param("selectedUserId", a.getId().toString(), b.getId().toString()))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    var messageId = messageIdFromRedirect(result);
    assertThat(recipients.countByMessageId(messageId)).isEqualTo(2);
  }

  /** 検索条件(AdminUserSearchCriteria)に一致する全員への配信は、AdminUserSearchServiceをそのまま再利用する。 */
  @Test
  void adminCanSendToAllUsersMatchingSearchCriteria() throws Exception {
    var admin = admin("criteria-admin@example.com");
    regularUser("criteria-match-a@example.com");
    regularUser("criteria-match-b@example.com");
    regularUser("other-user@example.com");

    var result =
        mvc.perform(
                post("/admin/messages")
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .with(csrf())
                    .param("title", "検索条件配信テスト")
                    .param("body", "本文です")
                    .param("targetMode", "CRITERIA")
                    .param("email", "criteria-match-"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    var messageId = messageIdFromRedirect(result);
    assertThat(recipients.countByMessageId(messageId)).isEqualTo(2);
  }

  @Test
  void adminHistoryShowsDeliveredReadAndUnreadCounts() throws Exception {
    var admin = admin("history-admin@example.com");
    var reader = regularUser("history-reader@example.com");
    var nonReader = regularUser("history-non-reader@example.com");

    var sendResult =
        mvc.perform(
                post("/admin/messages")
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .with(csrf())
                    .param("title", "履歴確認用メッセージ")
                    .param("body", "本文です")
                    .param("targetMode", "EXPLICIT")
                    .param("selectedUserId", reader.getId().toString(), nonReader.getId().toString()))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    var messageId = messageIdFromRedirect(sendResult);
    var message = messages.findById(messageId).orElseThrow();
    var readerRecipientId =
        recipients.findByUserIdOrderByMessageSentAtDesc(reader.getId(), org.springframework.data.domain.PageRequest.of(0, 10))
            .getContent()
            .get(0)
            .getId();
    // 通知詳細はGETで開いた時点(表示成功時)で既読になる仕様
    mvc.perform(get("/mypage/messages/{id}", readerRecipientId).with(user(reader.getEmail()).roles("USER")))
        .andExpect(status().isOk());

    mvc.perform(get("/admin/messages").with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/messages/list"));

    mvc.perform(get("/admin/messages/{id}", message.getId()).with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/messages/detail"))
        .andExpect(model().attribute("message", message));

    assertThat(recipients.countByMessageId(message.getId())).isEqualTo(2);
    assertThat(recipients.countByMessageIdAndReadAtIsNotNull(message.getId())).isEqualTo(1);
  }

  /**
   * spring.jpa.open-in-view=false(application.yml)のため、本番では1リクエスト=1トランザクション。
   * このテストクラスは@Transactionalだが、その仕組み(TransactionalTestExecutionListener)は
   * テストメソッド全体を1つの外側トランザクション(1つのHibernateセッション)でくるむため、
   * その中でMockMvcのリクエストを何回呼んでも同じセッションが使い回されてしまい、
   * message.createdByAdmin(LAZY)のようなLazyInitializationExceptionが本番では起きるのに
   * テストでは再現しない、という事故が起きる(実際に手動ブラウザ確認で検出・修正した)。
   * このテストメソッドだけPropagation.NOT_SUPPORTEDで外側トランザクションを無効化し、
   * 送信(POST)と詳細表示(GET)がそれぞれ独立したトランザクション/セッションで処理される
   * 本番のリクエスト境界を再現する。ロールバックされないため、他のテストの
   * messages.count()等(DB全体を見る絶対数の検証)を壊さないよう、finallyで必ず後片付けする。
   */
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void adminDetailScreenRendersCreatedByAdminAcrossASeparateTransactionFromTheSend() throws Exception {
    var admin = admin("cross-tx-admin@example.com");
    var target = regularUser("cross-tx-target@example.com");
    Long messageId = null;
    try {
      var sendResult =
          mvc.perform(
                  post("/admin/messages")
                      .with(user(admin.getEmail()).roles("ADMIN"))
                      .with(csrf())
                      .param("title", "トランザクション境界確認用メッセージ")
                      .param("body", "本文です")
                      .param("targetMode", "EXPLICIT")
                      .param("selectedUserId", target.getId().toString()))
              .andExpect(status().is3xxRedirection())
              .andReturn();
      messageId = messageIdFromRedirect(sendResult);

      mvc.perform(get("/admin/messages/{id}", messageId).with(user(admin.getEmail()).roles("ADMIN")))
          .andExpect(status().isOk())
          .andExpect(view().name("admin/messages/detail"))
          .andExpect(content().string(org.hamcrest.Matchers.containsString(admin.getDisplayName() + "さんが送信")));
    } finally {
      Long finalMessageId = messageId;
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                recipients.deleteByUserId(target.getId());
                if (finalMessageId != null) messages.deleteById(finalMessageId);
                users.delete(target);
                users.delete(admin);
              });
    }
  }
}
