package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * GA4タグ(gtag.js)を出力してよいかどうかの判定ロジックの単体テスト。
 * fragments/analytics.htmlはgaMeasurementIdが空文字/nullなら何も出力しないため、
 * このメソッドが正しい条件で空文字を返すことが、GA4計測の除外そのものを保証する。
 */
class NavigationAdviceTest {
  private NavigationAdvice advice(String environmentProfile) {
    var mockEnv = new MockEnvironment();
    if (environmentProfile != null) mockEnv.setActiveProfiles(environmentProfile);

    @SuppressWarnings("unchecked")
    ObjectProvider<UserRepository> usersProvider = mock(ObjectProvider.class);
    when(usersProvider.getIfAvailable()).thenReturn(users);

    @SuppressWarnings("unchecked")
    ObjectProvider<com.exradar.service.AccountService> accountProvider = mock(ObjectProvider.class);

    var advice = new NavigationAdvice(accountProvider, usersProvider, mockEnv);
    setGaMeasurementId(advice, "G-TESTID123");
    return advice;
  }

  private final UserRepository users = mock(UserRepository.class);

  private void setGaMeasurementId(NavigationAdvice advice, String value) {
    try {
      var field = NavigationAdvice.class.getDeclaredField("gaMeasurementId");
      field.setAccessible(true);
      field.set(advice, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private Principal principalFor(String email) {
    return () -> email;
  }

  /** Cookieを一切持たない、通常のリクエストを模したもの。 */
  private HttpServletRequest requestWithoutCookie() {
    return new MockHttpServletRequest();
  }

  /** 「このブラウザをアクセス解析から除外」設定済みのブラウザから来たリクエストを模したもの。 */
  private HttpServletRequest requestWithBrowserExclusionCookie() {
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie(NavigationAdvice.BROWSER_EXCLUSION_COOKIE, "1"));
    return request;
  }

  @Test
  void nonProdEnvironmentNeverEmitsGaTagEvenForAnonymous() {
    var advice = advice("dev");
    assertThat(advice.gaMeasurementId(null, requestWithoutCookie())).isEmpty();
  }

  @Test
  void testProfileNeverEmitsGaTag() {
    var advice = advice("test");
    assertThat(advice.gaMeasurementId(null, requestWithoutCookie())).isEmpty();
  }

  @Test
  void prodAndAnonymousUserIsMeasured() {
    var advice = advice("prod");
    assertThat(advice.gaMeasurementId(null, requestWithoutCookie())).isEqualTo("G-TESTID123");
  }

  @Test
  void prodAndRegularUserIsMeasured() {
    var advice = advice("prod");
    var user = new User("regular@example.com", "hash", "一般ユーザー", Role.USER);
    when(users.findByEmailIgnoreCase("regular@example.com")).thenReturn(Optional.of(user));

    assertThat(advice.gaMeasurementId(principalFor("regular@example.com"), requestWithoutCookie()))
        .isEqualTo("G-TESTID123");
  }

  @Test
  void prodAndAnalyticsExcludedUserIsNotMeasured() {
    var advice = advice("prod");
    var user = new User("excluded@example.com", "hash", "除外ユーザー", Role.USER);
    user.setAnalyticsExcluded(true);
    when(users.findByEmailIgnoreCase("excluded@example.com")).thenReturn(Optional.of(user));

    assertThat(advice.gaMeasurementId(principalFor("excluded@example.com"), requestWithoutCookie()))
        .isEmpty();
  }

  @Test
  void prodAndAdminIsNotMeasuredEvenWithFlagFalse() {
    var advice = advice("prod");
    var admin = new User("admin@example.com", "hash", "管理者", Role.ADMIN);
    // 明示的にfalseのままでも、ROLE_ADMINの判定だけで除外される(二重の安全策)。
    admin.setAnalyticsExcluded(false);
    when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));

    assertThat(advice.gaMeasurementId(principalFor("admin@example.com"), requestWithoutCookie()))
        .isEmpty();
  }

  @Test
  void reEnablingAnalyticsForPreviouslyExcludedUserResumesMeasurement() {
    var advice = advice("prod");
    var user = new User("was-excluded@example.com", "hash", "元除外ユーザー", Role.USER);
    user.setAnalyticsExcluded(true);
    when(users.findByEmailIgnoreCase("was-excluded@example.com")).thenReturn(Optional.of(user));
    assertThat(advice.gaMeasurementId(principalFor("was-excluded@example.com"), requestWithoutCookie()))
        .isEmpty();

    user.setAnalyticsExcluded(false);
    assertThat(advice.gaMeasurementId(principalFor("was-excluded@example.com"), requestWithoutCookie()))
        .isEqualTo("G-TESTID123");
  }

  @Test
  void loggedOutAfterExclusionIsMeasuredAsAnonymous() {
    var advice = advice("prod");
    // ログアウト後はPrincipalがnullになる(除外対象だったユーザーの情報は引き継がれない)。
    assertThat(advice.gaMeasurementId(null, requestWithoutCookie())).isEqualTo("G-TESTID123");
  }

  @Test
  void blankMeasurementIdNeverEmitsRegardlessOfEnvironmentOrUser() {
    var advice = advice("prod");
    setGaMeasurementId(advice, "");
    assertThat(advice.gaMeasurementId(null, requestWithoutCookie())).isEmpty();
  }

  // --- ここから「このブラウザを除外」Cookieに関するテスト ---

  @Test
  void browserExclusionCookieStopsMeasurementForAnonymousVisitor() {
    // 要望の核心: ログイン前(匿名)の状態でも、このブラウザ由来のアクセスは計測しない。
    var advice = advice("prod");
    assertThat(advice.gaMeasurementId(null, requestWithBrowserExclusionCookie())).isEmpty();
  }

  @Test
  void browserExclusionCookieStopsMeasurementEvenForRegularLoggedInUser() {
    // ブラウザ除外は、ログイン後の一般ユーザー(analyticsExcluded=false)であっても優先される。
    var advice = advice("prod");
    var user = new User("regular-on-excluded-browser@example.com", "hash", "一般ユーザー", Role.USER);
    when(users.findByEmailIgnoreCase("regular-on-excluded-browser@example.com"))
        .thenReturn(Optional.of(user));

    assertThat(
            advice.gaMeasurementId(
                principalFor("regular-on-excluded-browser@example.com"),
                requestWithBrowserExclusionCookie()))
        .isEmpty();
  }

  @Test
  void withoutExclusionCookieAnonymousIsStillMeasuredAsBefore() {
    // Cookieが無いブラウザ(=一般の未ログイン利用者)は従来どおり計測される。
    var advice = advice("prod");
    assertThat(advice.gaMeasurementId(null, requestWithoutCookie())).isEqualTo("G-TESTID123");
  }

  @Test
  void isBrowserExcludedReturnsFalseWhenNoCookiesArePresent() {
    assertThat(NavigationAdvice.isBrowserExcluded(requestWithoutCookie())).isFalse();
  }

  @Test
  void isBrowserExcludedReturnsFalseWhenOtherCookiesArePresentButNotThisOne() {
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie("JSESSIONID", "abc123"));
    assertThat(NavigationAdvice.isBrowserExcluded(request)).isFalse();
  }

  @Test
  void isBrowserExcludedReturnsTrueOnlyForExactValueOne() {
    var request = new MockHttpServletRequest();
    request.setCookies(new Cookie(NavigationAdvice.BROWSER_EXCLUSION_COOKIE, "0"));
    assertThat(NavigationAdvice.isBrowserExcluded(request)).isFalse();
  }

  @Test
  void isBrowserExcludedReturnsTrueWhenCookieIsSet() {
    assertThat(NavigationAdvice.isBrowserExcluded(requestWithBrowserExclusionCookie())).isTrue();
  }
}
