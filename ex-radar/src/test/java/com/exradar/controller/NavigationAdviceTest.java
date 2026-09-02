package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.UserRepository;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

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

  @Test
  void nonProdEnvironmentNeverEmitsGaTagEvenForAnonymous() {
    var advice = advice("dev");
    assertThat(advice.gaMeasurementId(null)).isEmpty();
  }

  @Test
  void testProfileNeverEmitsGaTag() {
    var advice = advice("test");
    assertThat(advice.gaMeasurementId(null)).isEmpty();
  }

  @Test
  void prodAndAnonymousUserIsMeasured() {
    var advice = advice("prod");
    assertThat(advice.gaMeasurementId(null)).isEqualTo("G-TESTID123");
  }

  @Test
  void prodAndRegularUserIsMeasured() {
    var advice = advice("prod");
    var user = new User("regular@example.com", "hash", "一般ユーザー", Role.USER);
    when(users.findByEmailIgnoreCase("regular@example.com")).thenReturn(Optional.of(user));

    assertThat(advice.gaMeasurementId(principalFor("regular@example.com"))).isEqualTo("G-TESTID123");
  }

  @Test
  void prodAndAnalyticsExcludedUserIsNotMeasured() {
    var advice = advice("prod");
    var user = new User("excluded@example.com", "hash", "除外ユーザー", Role.USER);
    user.setAnalyticsExcluded(true);
    when(users.findByEmailIgnoreCase("excluded@example.com")).thenReturn(Optional.of(user));

    assertThat(advice.gaMeasurementId(principalFor("excluded@example.com"))).isEmpty();
  }

  @Test
  void prodAndAdminIsNotMeasuredEvenWithFlagFalse() {
    var advice = advice("prod");
    var admin = new User("admin@example.com", "hash", "管理者", Role.ADMIN);
    // 明示的にfalseのままでも、ROLE_ADMINの判定だけで除外される(二重の安全策)。
    admin.setAnalyticsExcluded(false);
    when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));

    assertThat(advice.gaMeasurementId(principalFor("admin@example.com"))).isEmpty();
  }

  @Test
  void reEnablingAnalyticsForPreviouslyExcludedUserResumesMeasurement() {
    var advice = advice("prod");
    var user = new User("was-excluded@example.com", "hash", "元除外ユーザー", Role.USER);
    user.setAnalyticsExcluded(true);
    when(users.findByEmailIgnoreCase("was-excluded@example.com")).thenReturn(Optional.of(user));
    assertThat(advice.gaMeasurementId(principalFor("was-excluded@example.com"))).isEmpty();

    user.setAnalyticsExcluded(false);
    assertThat(advice.gaMeasurementId(principalFor("was-excluded@example.com"))).isEqualTo("G-TESTID123");
  }

  @Test
  void loggedOutAfterExclusionIsMeasuredAsAnonymous() {
    var advice = advice("prod");
    // ログアウト後はPrincipalがnullになる(除外対象だったユーザーの情報は引き継がれない)。
    assertThat(advice.gaMeasurementId(null)).isEqualTo("G-TESTID123");
  }

  @Test
  void blankMeasurementIdNeverEmitsRegardlessOfEnvironmentOrUser() {
    var advice = advice("prod");
    setGaMeasurementId(advice, "");
    assertThat(advice.gaMeasurementId(null)).isEmpty();
  }
}
