package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.exradar.entity.AdminAnnouncementRecipient;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.AdminAnnouncementRecipientRepository;
import com.exradar.repository.UserRepository;
import com.exradar.service.AdminAnnouncementService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ログイン後お知らせモーダルの表示条件・「同一ログインセッション中は原則1回のみ表示」・
 * 「次回以降表示しない」を、NavigationAdvice(pendingAnnouncement)とAccountControllerの
 * dismissエンドポイントを通じて実際のHTTPリクエストで確認する。
 * MockHttpSessionを明示的に使い回すことで「同一ブラウザセッション内の複数ページ遷移」を、
 * 使い回さないことで「ログアウト後の新しいセッション(次回ログイン相当)」を再現する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AnnouncementModalDisplayTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired AdminAnnouncementService service;
  @Autowired AdminAnnouncementRecipientRepository recipients;
  @Autowired PasswordEncoder encoder;

  private User admin(String email) {
    return users.save(new User(email, encoder.encode("password123"), "運営", Role.ADMIN));
  }

  private User regularUser(String email) {
    return users.save(new User(email, encoder.encode("password123"), "一般ユーザー", Role.USER));
  }

  private Long publishActiveAnnouncementFor(User admin, User target, String title) {
    var announcement =
        service.create(
            title, "本文です", null, LocalDateTime.now().minusDays(1), null, 0, admin.getEmail(), java.util.List.of(target.getId()));
    service.publish(announcement.getId());
    return announcement.getId();
  }

  @Test
  void anonymousNeverGetsAPendingAnnouncement() throws Exception {
    mvc.perform(get("/")).andExpect(status().isOk()).andExpect(model().attribute("pendingAnnouncement", nullValue()));
  }

  @Test
  void loggedInUserSeesActiveAnnouncementOnFirstPageLoad() throws Exception {
    var admin = admin("modal-first-admin@example.com");
    var target = regularUser("modal-first-target@example.com");
    publishActiveAnnouncementFor(admin, target, "初回表示お知らせ");

    var result =
        mvc.perform(get("/mypage").with(user(target.getEmail()).roles("USER")))
            .andExpect(status().isOk())
            .andReturn();

    var pending = (AdminAnnouncementRecipient) result.getModelAndView().getModel().get("pendingAnnouncement");
    assertThat(pending).isNotNull();
    assertThat(pending.getAnnouncement().getTitle()).isEqualTo("初回表示お知らせ");
    assertThat(pending.getDisplayCount()).isEqualTo(1);
  }

  @Test
  void sameSessionDoesNotShowTheModalAgainOnASecondPageLoad() throws Exception {
    var admin = admin("modal-samesession-admin@example.com");
    var target = regularUser("modal-samesession-target@example.com");
    publishActiveAnnouncementFor(admin, target, "同一セッション確認お知らせ");

    var first =
        mvc.perform(get("/mypage").with(user(target.getEmail()).roles("USER")))
            .andExpect(status().isOk())
            .andReturn();
    var session = (MockHttpSession) first.getRequest().getSession();
    assertThat(first.getModelAndView().getModel().get("pendingAnnouncement")).isNotNull();

    mvc.perform(get("/mypage").with(user(target.getEmail()).roles("USER")).session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("pendingAnnouncement", nullValue()));
  }

  @Test
  void aNewSessionShowsTheAnnouncementAgainLikeANextLogin() throws Exception {
    var admin = admin("modal-nextlogin-admin@example.com");
    var target = regularUser("modal-nextlogin-target@example.com");
    publishActiveAnnouncementFor(admin, target, "次回ログイン確認お知らせ");

    var first =
        mvc.perform(get("/mypage").with(user(target.getEmail()).roles("USER"))).andExpect(status().isOk()).andReturn();
    assertThat(first.getModelAndView().getModel().get("pendingAnnouncement")).isNotNull();

    // .session(...)を付けない = 新しいセッション(ログアウト後の次回ログインを想定)
    var second =
        mvc.perform(get("/mypage").with(user(target.getEmail()).roles("USER"))).andExpect(status().isOk()).andReturn();
    var pending = (AdminAnnouncementRecipient) second.getModelAndView().getModel().get("pendingAnnouncement");
    assertThat(pending).isNotNull();
    assertThat(pending.getDisplayCount()).isEqualTo(2);
  }

  @Test
  void dismissingPermanentlyStopsDisplayEvenInANewSession() throws Exception {
    var admin = admin("modal-dismiss-admin@example.com");
    var target = regularUser("modal-dismiss-target@example.com");
    publishActiveAnnouncementFor(admin, target, "永久非表示確認お知らせ");

    var first =
        mvc.perform(get("/mypage").with(user(target.getEmail()).roles("USER"))).andExpect(status().isOk()).andReturn();
    var pending = (AdminAnnouncementRecipient) first.getModelAndView().getModel().get("pendingAnnouncement");
    assertThat(pending).isNotNull();

    mvc.perform(
            post("/mypage/announcements/{id}/dismiss", pending.getId())
                .with(user(target.getEmail()).roles("USER"))
                .with(csrf()))
        .andExpect(status().isNoContent());

    // 新しいセッション(次回ログイン相当)でも、永久非表示にしたお知らせは二度と表示されない
    mvc.perform(get("/mypage").with(user(target.getEmail()).roles("USER")))
        .andExpect(status().isOk())
        .andExpect(model().attribute("pendingAnnouncement", nullValue()));
    assertThat(recipients.findById(pending.getId()).orElseThrow().isDismissedPermanently()).isTrue();
  }

  @Test
  void anotherUsersAnnouncementStateCannotBeDismissedByAttacker() throws Exception {
    var admin = admin("modal-idor-admin@example.com");
    var owner = regularUser("modal-idor-owner@example.com");
    var attacker = regularUser("modal-idor-attacker@example.com");
    publishActiveAnnouncementFor(admin, owner, "本人限定お知らせ");

    var first =
        mvc.perform(get("/mypage").with(user(owner.getEmail()).roles("USER"))).andExpect(status().isOk()).andReturn();
    var pending = (AdminAnnouncementRecipient) first.getModelAndView().getModel().get("pendingAnnouncement");
    assertThat(pending).isNotNull();

    mvc.perform(
            post("/mypage/announcements/{id}/dismiss", pending.getId())
                .with(user(attacker.getEmail()).roles("USER"))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertThat(recipients.findById(pending.getId()).orElseThrow().isDismissedPermanently()).isFalse();
  }

  @Test
  void dismissRequiresCsrf() throws Exception {
    var admin = admin("modal-csrf-admin@example.com");
    var target = regularUser("modal-csrf-target@example.com");
    publishActiveAnnouncementFor(admin, target, "CSRF確認お知らせ");
    var first =
        mvc.perform(get("/mypage").with(user(target.getEmail()).roles("USER"))).andExpect(status().isOk()).andReturn();
    var pending = (AdminAnnouncementRecipient) first.getModelAndView().getModel().get("pendingAnnouncement");

    mvc.perform(post("/mypage/announcements/{id}/dismiss", pending.getId()).with(user(target.getEmail()).roles("USER")))
        .andExpect(status().isForbidden());
    assertThat(recipients.findById(pending.getId()).orElseThrow().isDismissedPermanently()).isFalse();
  }
}
