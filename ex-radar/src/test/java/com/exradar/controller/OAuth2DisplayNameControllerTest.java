package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.User;
import com.exradar.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Googleログイン初回時の「表示名を設定してください」導線(DisplayNameSetupInterceptor含む)のテスト。
 * ここでは表示名設定ステップだけを単独で検証するため、各テストユーザーは
 * (規約同意は既に済んでいる=termsConsentPending=false)状態で作っている
 * (規約同意そのものの導線・優先順序はTermsConsentInterceptorTest/OAuth2ConsentFlowTest側で検証する)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OAuth2DisplayNameControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  private User googleUserWithConsentAlreadyCompleted(String email, String sub) {
    var user = User.forGoogleSignup(email, sub, "新規ユーザー", encoder.encode("x"));
    user.agreeToTermsAndPrivacyPolicy(LocalDateTime.now());
    return users.save(user);
  }

  @Test
  void userWithPendingDisplayNameIsRedirectedToSetupPage() throws Exception {
    User google = googleUserWithConsentAlreadyCompleted("pending-google-user@example.com", "sub-abc");

    mvc.perform(get("/").with(user(google.getEmail()).roles("USER")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/oauth2/display-name"));
  }

  @Test
  void setupPageItselfIsNotRedirected() throws Exception {
    User google = googleUserWithConsentAlreadyCompleted("setup-page-user@example.com", "sub-def");

    mvc.perform(get("/oauth2/display-name").with(user(google.getEmail()).roles("USER")))
        .andExpect(status().isOk())
        .andExpect(view().name("auth/display-name"));
  }

  @Test
  void submittingDisplayNameClearsPendingFlagAndAllowsNormalAccess() throws Exception {
    User google = googleUserWithConsentAlreadyCompleted("completes-setup@example.com", "sub-ghi");

    mvc.perform(
            post("/oauth2/display-name")
                .with(user(google.getEmail()).roles("USER"))
                .with(csrf())
                .param("displayName", "わたしの表示名"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));

    User updated = users.findById(google.getId()).orElseThrow();
    assertThat(updated.isDisplayNamePending()).isFalse();
    assertThat(updated.getDisplayName()).isEqualTo("わたしの表示名");

    mvc.perform(get("/").with(user(google.getEmail()).roles("USER"))).andExpect(status().isOk());
  }

  @Test
  void blankDisplayNameIsRejected() throws Exception {
    User google = googleUserWithConsentAlreadyCompleted("blank-name-user@example.com", "sub-jkl");

    mvc.perform(
            post("/oauth2/display-name")
                .with(user(google.getEmail()).roles("USER"))
                .with(csrf())
                .param("displayName", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("auth/display-name"))
        .andExpect(model().attributeHasFieldErrors("displayNameForm", "displayName"));

    assertThat(users.findById(google.getId()).orElseThrow().isDisplayNamePending()).isTrue();
  }

  @Test
  void ordinaryLocalUserIsNotAffectedByInterceptor() throws Exception {
    User local =
        users.save(new User("ordinary-local-user@example.com", encoder.encode("password123"), "たろう", com.exradar.entity.Role.USER));

    mvc.perform(get("/").with(user(local.getEmail()).roles("USER"))).andExpect(status().isOk());
  }
}
