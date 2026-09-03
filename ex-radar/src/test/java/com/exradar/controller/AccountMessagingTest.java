package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.AdminMessageRecipientRepository;
import com.exradar.repository.UserRepository;
import com.exradar.service.AdminMessagingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ユーザー側の通知一覧(/mypage/notifications)・運営メッセージ詳細(/mypage/messages/{id})。
 * 「自分宛のものしか見えない」ことと、他ユーザー宛のIDをURLへ直打ちしても閲覧できない
 * (IDOR対策)ことがこの機能のセキュリティ上の核心のため、重点的に確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountMessagingTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired AdminMessagingService messaging;
  @Autowired AdminMessageRecipientRepository recipients;
  @Autowired PasswordEncoder encoder;

  private User admin(String email) {
    return users.save(new User(email, encoder.encode("password123"), "運営", Role.ADMIN));
  }

  private User regularUser(String email) {
    return users.save(new User(email, encoder.encode("password123"), "一般ユーザー", Role.USER));
  }

  @Test
  void anonymousCannotAccessNotificationScreens() throws Exception {
    mvc.perform(get("/mypage/notifications")).andExpect(status().is3xxRedirection());
    mvc.perform(get("/mypage/messages/{id}", 1L)).andExpect(status().is3xxRedirection());
  }

  @Test
  void loggedInUsersOwnNotificationListDoesNotContainOtherUsersMessages() throws Exception {
    var admin = admin("account-msg-admin@example.com");
    var owner = regularUser("account-msg-owner@example.com");
    var other = regularUser("account-msg-other@example.com");
    messaging.send("本人宛タイトル", "本文", null, admin.getEmail(), java.util.List.of(owner.getId()));
    messaging.send("他人宛タイトル", "本文", null, admin.getEmail(), java.util.List.of(other.getId()));

    var result =
        mvc.perform(get("/mypage/notifications").with(user(owner.getEmail()).roles("USER")))
            .andExpect(status().isOk())
            .andExpect(view().name("account/notifications"))
            .andReturn();

    @SuppressWarnings("unchecked")
    var messageResult =
        (org.springframework.data.domain.Page<com.exradar.dto.UserMessageSummaryDto>)
            result.getModelAndView().getModel().get("messageResult");
    assertThat(messageResult.getContent())
        .extracting(com.exradar.dto.UserMessageSummaryDto::title)
        .containsExactly("本人宛タイトル");
  }

  @Test
  void openingOwnNotificationShowsItAndMarksItRead() throws Exception {
    var admin = admin("account-msg-open-admin@example.com");
    var owner = regularUser("account-msg-open-owner@example.com");
    messaging.send("開封確認タイトル", "本文の内容です", null, admin.getEmail(), java.util.List.of(owner.getId()));
    var recipientId =
        recipients.findByUserIdOrderByMessageSentAtDesc(owner.getId(), PageRequest.of(0, 10)).getContent().get(0).getId();

    mvc.perform(get("/mypage/messages/{id}", recipientId).with(user(owner.getEmail()).roles("USER")))
        .andExpect(status().isOk())
        .andExpect(view().name("account/message-detail"));

    assertThat(recipients.findById(recipientId).orElseThrow().isRead()).isTrue();
  }

  /** 他ユーザー宛の通知IDをURLへ直打ちしても、404として扱われ本文は閲覧できない(IDOR対策)。 */
  @Test
  void anotherUsersNotificationDetailCannotBeOpenedByGuessingTheId() throws Exception {
    var admin = admin("account-msg-idor-admin@example.com");
    var owner = regularUser("account-msg-idor-owner@example.com");
    var attacker = regularUser("account-msg-idor-attacker@example.com");
    messaging.send("本人だけに見えるはずのタイトル", "本文", null, admin.getEmail(), java.util.List.of(owner.getId()));
    var recipientId =
        recipients.findByUserIdOrderByMessageSentAtDesc(owner.getId(), PageRequest.of(0, 10)).getContent().get(0).getId();

    mvc.perform(get("/mypage/messages/{id}", recipientId).with(user(attacker.getEmail()).roles("USER")))
        .andExpect(status().isNotFound());
    assertThat(recipients.findById(recipientId).orElseThrow().isRead()).isFalse();
  }

  @Test
  void unreadBadgeCountReflectsAdminMessagesOnTheBottomNav() throws Exception {
    var admin = admin("account-msg-badge-admin@example.com");
    var owner = regularUser("account-msg-badge-owner@example.com");
    messaging.send("バッジ確認1", "本文", null, admin.getEmail(), java.util.List.of(owner.getId()));
    messaging.send("バッジ確認2", "本文", null, admin.getEmail(), java.util.List.of(owner.getId()));

    mvc.perform(get("/mypage").with(user(owner.getEmail()).roles("USER")))
        .andExpect(status().isOk())
        .andExpect(model().attribute("unreadNotificationCount", 2L));
  }
}
