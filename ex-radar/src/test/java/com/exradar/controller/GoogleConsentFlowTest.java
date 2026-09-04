package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Role;
import com.exradar.entity.User;
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
 * Google認証で「初めて」EXレーダーのユーザーになった人だけに規約同意を強制する仕組み
 * (User.termsConsentPending、TermsConsentInterceptor、/auth/consent)の検証。
 * Googleへの実際のネットワーク通信は行わず、.with(user(...))でログイン状態を模擬したうえで
 * Controller/Security(HandlerInterceptor)レイヤーを直接検証する(既存のOAuth2DisplayName
 * ControllerTestと同じ方針)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GoogleConsentFlowTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  private User newGoogleUser(String email, String sub) {
    return users.save(User.forGoogleSignup(email, sub, "新規ユーザー", encoder.encode("x")));
  }

  @Test
  void newGoogleUserIsRedirectedToConsentPageFromAnyOrdinaryPage() throws Exception {
    var google = newGoogleUser("new-google-user@example.com", "sub-new-1");

    mvc.perform(get("/").with(user(google.getEmail()).roles("USER")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/auth/consent"));
  }

  @Test
  void consentPageItselfIsAccessibleWhilePending() throws Exception {
    var google = newGoogleUser("consent-page-access@example.com", "sub-new-2");

    mvc.perform(get("/auth/consent").with(user(google.getEmail()).roles("USER")))
        .andExpect(status().isOk())
        .andExpect(view().name("auth/consent"));
  }

  /** 同意前でも/terms・/privacy・/contact・/guidelines・/logoutなど最低限のページは閲覧できる。 */
  @Test
  void allowedPagesRemainAccessibleWhileConsentIsPending() throws Exception {
    var google = newGoogleUser("allowed-pages-pending@example.com", "sub-new-3");

    mvc.perform(get("/terms").with(user(google.getEmail()).roles("USER"))).andExpect(status().isOk());
    mvc.perform(get("/privacy").with(user(google.getEmail()).roles("USER"))).andExpect(status().isOk());
    mvc.perform(get("/contact").with(user(google.getEmail()).roles("USER"))).andExpect(status().isOk());
    mvc.perform(get("/guidelines").with(user(google.getEmail()).roles("USER"))).andExpect(status().isOk());
  }

  /** 同意画面を経由せず/oauth2/display-name等の他ページへ直接アクセスしても、同意画面へ差し戻される(回避不可)。 */
  @Test
  void directAccessToOtherPagesCannotBypassConsent() throws Exception {
    var google = newGoogleUser("bypass-attempt@example.com", "sub-new-4");

    mvc.perform(get("/oauth2/display-name").with(user(google.getEmail()).roles("USER")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/auth/consent"));
    mvc.perform(get("/mypage").with(user(google.getEmail()).roles("USER")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/auth/consent"));
    mvc.perform(get("/experiences").with(user(google.getEmail()).roles("USER")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/auth/consent"));
  }

  @Test
  void submittingWithoutCheckingTheBoxIsRejectedAndStaysPending() throws Exception {
    var google = newGoogleUser("no-check-consent@example.com", "sub-new-5");

    mvc.perform(post("/auth/consent").with(user(google.getEmail()).roles("USER")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(view().name("auth/consent"))
        .andExpect(model().attributeHasFieldErrors("termsConsentForm", "agreedToTerms"));

    var stillPending = users.findById(google.getId()).orElseThrow();
    assertThat(stillPending.isTermsConsentPending()).isTrue();
    assertThat(stillPending.getTermsAgreedAt()).isNull();
  }

  @Test
  void agreeingRecordsTimestampsClearsPendingFlagAndProceedsToDisplayNameSetup() throws Exception {
    var google = newGoogleUser("agrees-to-consent@example.com", "sub-new-6");

    mvc.perform(
            post("/auth/consent")
                .with(user(google.getEmail()).roles("USER"))
                .with(csrf())
                .param("agreedToTerms", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/oauth2/display-name"));

    var updated = users.findById(google.getId()).orElseThrow();
    assertThat(updated.isTermsConsentPending()).isFalse();
    assertThat(updated.getTermsAgreedAt()).isNotNull();
    assertThat(updated.getPrivacyPolicyAgreedAt()).isNotNull();

    // 同意後は他の画面(/oauth2/display-nameを含む)へ通常どおりアクセスできる(同意画面へ戻されない)。
    mvc.perform(get("/oauth2/display-name").with(user(google.getEmail()).roles("USER")))
        .andExpect(status().isOk())
        .andExpect(view().name("auth/display-name"));
  }

  /**
   * 既存のGoogleユーザー(termsConsentPending=false)は、termsAgreedAtがNULLでも強制同意の対象にならない。
   * forGoogleSignup()は「これから起きる新規登録」を表すため常にtermsConsentPending=trueで作られる
   * (本機能導入後の新規登録は必ずこの状態になる)。「本機能導入より前から存在するGoogleユーザー」を
   * 再現するには、マイグレーションのDEFAULT FALSEによる後方互換の結果と同じ状態
   * (termsConsentPending=false・termsAgreedAt=null)を直接作る必要があるため、
   * AdminUserSearchServiceTest#reflectivelySetCreatedAtと同じ考え方でリフレクションを使う。
   */
  @Test
  void existingGoogleUserWithNullTermsAgreedAtIsNotForcedToConsent() throws Exception {
    var existingGoogle = User.forGoogleSignup("existing-google-user@example.com", "sub-existing-1", "既存ユーザー", encoder.encode("x"));
    // 表示名設定は既に完了しているものとする(実際の既存ユーザーは初回ログイン時にこの画面を通過済み)。
    existingGoogle.completeDisplayNameSetup("既存の表示名");
    reflectivelyClearTermsConsentPending(existingGoogle);
    users.save(existingGoogle);

    assertThat(existingGoogle.isTermsConsentPending()).isFalse();
    assertThat(existingGoogle.getTermsAgreedAt()).isNull();

    mvc.perform(get("/").with(user(existingGoogle.getEmail()).roles("USER"))).andExpect(status().isOk());
    mvc.perform(get("/mypage").with(user(existingGoogle.getEmail()).roles("USER"))).andExpect(status().isOk());
  }

  /** 通常登録(LOCAL)ユーザーはtermsConsentPending機構と無関係で、これまでどおりアクセスできる。 */
  @Test
  void ordinaryLocalUserIsUnaffectedByConsentInterceptor() throws Exception {
    var local = users.save(new User("consent-local-user@example.com", encoder.encode("password123"), "ローカルユーザー", Role.USER));
    assertThat(local.isTermsConsentPending()).isFalse();

    mvc.perform(get("/").with(user(local.getEmail()).roles("USER"))).andExpect(status().isOk());
  }

  @Test
  void anonymousUserIsNotAffectedByConsentInterceptor() throws Exception {
    mvc.perform(get("/")).andExpect(status().isOk());
  }

  private void reflectivelyClearTermsConsentPending(User user) {
    try {
      var field = User.class.getDeclaredField("termsConsentPending");
      field.setAccessible(true);
      field.set(user, false);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
