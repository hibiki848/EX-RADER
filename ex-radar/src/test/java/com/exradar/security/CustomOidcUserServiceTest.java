package com.exradar.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exradar.entity.AuthProvider;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.UserRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * CustomOidcUserService#process(OidcUser)の単体テスト。
 * super.loadUser()(実際のGoogle通信)は行わず、OidcUserを直接組み立てて渡す。
 */
@ExtendWith(MockitoExtension.class)
class CustomOidcUserServiceTest {
  @Mock UserRepository users;
  @Mock PasswordEncoder encoder;

  private CustomOidcUserService service;

  @BeforeEach
  void setUp() {
    service = new CustomOidcUserService(users, encoder);
  }

  @Test
  void createsNewUserOnFirstGoogleLogin() {
    when(users.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "sub-123"))
        .thenReturn(Optional.empty());
    when(users.existsByEmailIgnoreCase("new-google-user@example.com")).thenReturn(false);
    when(encoder.encode(anyString())).thenReturn("encoded-random-password");
    when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    OidcUser result = service.process(oidcUser("sub-123", "new-google-user@example.com", true));

    assertThat(result.getName()).isEqualTo("new-google-user@example.com");
    assertThat(result.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");

    var captor = org.mockito.ArgumentCaptor.forClass(User.class);
    verify(users).save(captor.capture());
    User created = captor.getValue();
    assertThat(created.getEmail()).isEqualTo("new-google-user@example.com");
    assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(created.getProviderUserId()).isEqualTo("sub-123");
    assertThat(created.isDisplayNamePending()).isTrue();
    assertThat(created.getDisplayName()).isNotEqualTo("Google 太郎");
  }

  @Test
  void returnsExistingUserOnRepeatGoogleLogin() {
    User existing = new User("returning@example.com", "hash", "表示名", Role.USER);
    when(users.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "sub-456"))
        .thenReturn(Optional.of(existing));

    OidcUser result = service.process(oidcUser("sub-456", "returning@example.com", true));

    assertThat(result.getName()).isEqualTo("returning@example.com");
    verify(users, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void rejectsLoginWhenEmailAlreadyRegisteredLocally() {
    when(users.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "sub-789"))
        .thenReturn(Optional.empty());
    when(users.existsByEmailIgnoreCase("existing-local@example.com")).thenReturn(true);

    assertThatThrownBy(() -> service.process(oidcUser("sub-789", "existing-local@example.com", true)))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .satisfies(
            e ->
                assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                    .isEqualTo("account_exists"));
    verify(users, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void rejectsLoginWhenEmailNotVerified() {
    assertThatThrownBy(() -> service.process(oidcUser("sub-999", "unverified@example.com", false)))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .satisfies(
            e ->
                assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                    .isEqualTo("email_not_verified"));
  }

  @Test
  void rejectsLoginWhenEmailMissing() {
    assertThatThrownBy(() -> service.process(oidcUser("sub-000", null, null)))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .satisfies(
            e ->
                assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                    .isEqualTo("email_not_available"));
  }

  @Test
  void rejectsSuspendedGoogleAccount() {
    User suspended = new User("suspended@example.com", "hash", "表示名", Role.USER);
    suspended.setSuspended(true);
    when(users.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "sub-111"))
        .thenReturn(Optional.of(suspended));

    assertThatThrownBy(() -> service.process(oidcUser("sub-111", "suspended@example.com", true)))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .satisfies(
            e ->
                assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                    .isEqualTo("account_suspended"));
  }

  private OidcUser oidcUser(String sub, String email, Boolean emailVerified) {
    Map<String, Object> claims = new HashMap<>();
    if (sub != null) claims.put(IdTokenClaimNames.SUB, sub);
    claims.put(IdTokenClaimNames.ISS, "https://accounts.google.com");
    claims.put(IdTokenClaimNames.AUD, List.of("test-client-id"));
    claims.put(IdTokenClaimNames.IAT, Instant.now());
    claims.put(IdTokenClaimNames.EXP, Instant.now().plusSeconds(3600));
    // Googleの氏名がそのまま公開表示名にならないことを確認するためのダミー値
    claims.put(StandardClaimNames.NAME, "Google 太郎");
    if (email != null) claims.put(StandardClaimNames.EMAIL, email);
    if (emailVerified != null) claims.put(StandardClaimNames.EMAIL_VERIFIED, emailVerified);

    OidcIdToken idToken =
        new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
    OidcUserInfo userInfo = new OidcUserInfo(claims);
    return new DefaultOidcUser(List.of(), idToken, userInfo);
  }
}
