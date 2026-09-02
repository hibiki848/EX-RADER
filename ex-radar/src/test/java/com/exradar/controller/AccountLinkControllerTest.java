package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.UserRepository;
import com.exradar.security.OAuth2LoginFailureHandler;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 「Googleアカウントで続ける」で既存のメール+パスワードアカウントに連携する画面のテスト。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountLinkControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  @Test
  void withoutPendingLinkRedirectsToLogin() throws Exception {
    mvc.perform(get("/oauth2/link-account"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void showsFormWhenLinkIsPending() throws Exception {
    users.save(
        new User("link-target1@example.com", encoder.encode("password123"), "既存ユーザー", Role.USER));
    MockHttpSession session = pendingLinkSession("sub-abc", "link-target1@example.com");

    mvc.perform(get("/oauth2/link-account").session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("auth/link-account"))
        .andExpect(model().attribute("email", "link-target1@example.com"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("link-target1@example.com")));
  }

  @Test
  void correctPasswordLinksAccountAndLogsIn() throws Exception {
    User existing =
        users.save(
            new User("link-target2@example.com", encoder.encode("password123"), "既存ユーザー", Role.USER));
    MockHttpSession session = pendingLinkSession("sub-def", "link-target2@example.com");

    var result =
        mvc.perform(
                post("/oauth2/link-account")
                    .session(session)
                    .with(csrf())
                    .param("password", "password123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"))
            .andReturn();

    User linked = users.findById(existing.getId()).orElseThrow();
    assertThat(linked.getProviderUserId()).isEqualTo("sub-def");
    // メール+パスワードでのログイン能力(パスワードハッシュ)は変更されていない
    assertThat(linked.getPassword()).isEqualTo(existing.getPassword());

    // 連携後、同じセッションで認証状態になっている(マイページ等が閲覧できる)ことを確認
    HttpSession authenticatedSession = result.getRequest().getSession(false);
    mvc.perform(get("/mypage").session((MockHttpSession) authenticatedSession))
        .andExpect(status().isOk());
  }

  @Test
  void wrongPasswordDoesNotLinkAndShowsError() throws Exception {
    User existing =
        users.save(
            new User("link-target3@example.com", encoder.encode("password123"), "既存ユーザー", Role.USER));
    MockHttpSession session = pendingLinkSession("sub-ghi", "link-target3@example.com");

    mvc.perform(
            post("/oauth2/link-account")
                .session(session)
                .with(csrf())
                .param("password", "wrong-password"))
        .andExpect(status().isOk())
        .andExpect(view().name("auth/link-account"))
        .andExpect(model().attribute("linkError", "パスワードが正しくありません。"));

    User unchanged = users.findById(existing.getId()).orElseThrow();
    assertThat(unchanged.getProviderUserId()).isNull();
  }

  @Test
  void expiredPendingLinkRedirectsToLogin() throws Exception {
    users.save(
        new User("link-target4@example.com", encoder.encode("password123"), "既存ユーザー", Role.USER));
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(OAuth2LoginFailureHandler.SESSION_ATTR_SUB, "sub-jkl");
    session.setAttribute(OAuth2LoginFailureHandler.SESSION_ATTR_EMAIL, "link-target4@example.com");
    session.setAttribute(
        OAuth2LoginFailureHandler.SESSION_ATTR_ISSUED_AT,
        System.currentTimeMillis() - java.util.concurrent.TimeUnit.MINUTES.toMillis(20));

    mvc.perform(get("/oauth2/link-account").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  private MockHttpSession pendingLinkSession(String sub, String email) {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(OAuth2LoginFailureHandler.SESSION_ATTR_SUB, sub);
    session.setAttribute(OAuth2LoginFailureHandler.SESSION_ATTR_EMAIL, email);
    session.setAttribute(OAuth2LoginFailureHandler.SESSION_ATTR_ISSUED_AT, System.currentTimeMillis());
    return session;
  }
}
