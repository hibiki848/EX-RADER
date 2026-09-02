package com.exradar.security;

import com.exradar.entity.AuthProvider;
import com.exradar.entity.User;
import com.exradar.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Googleログイン(OIDC)のユーザー取得・初回登録処理。
 * Spring SecurityのOidcUserServiceを継承し、標準のトークン検証・userinfo取得はそのまま利用したうえで、
 * EXレーダー側のUser(初回なら作成、既存ならそのまま)に紐付けるのがこのクラスの役割。
 *
 * 重要な設計判断:
 * - 突合キーはメールアドレスではなくOIDCの sub(Google側の一意ID)。メールアドレスは変更され得るため。
 * - 同じメールアドレスのLOCALアカウントが既に存在する場合は自動統合せず、ログインを拒否する
 *   (アカウント乗っ取り防止。「同じメールアドレスのアカウントが既に存在します」として案内する)。
 * - 返すOidcUserのname属性キーを"email"に固定することで、Authentication#getName()が
 *   既存のフォームログイン(ExRadarUserDetailsService)と同じくメールアドレスを返すようにしている。
 *   これにより既存コントローラーのPrincipal#getName()利用箇所は一切変更不要になる。
 */
@Service
public class CustomOidcUserService extends OidcUserService {
  private static final Logger log = LoggerFactory.getLogger(CustomOidcUserService.class);

  private final UserRepository users;
  private final PasswordEncoder encoder;

  public CustomOidcUserService(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  @Override
  @Transactional
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    return process(super.loadUser(userRequest));
  }

  /**
   * super.loadUser()が返したOidcUserを受け取り、EXレーダー側のUserへの突合・
   * 初回登録を行う本体ロジック。ネットワーク呼び出しを含まないため単体テストで直接検証できる。
   */
  OidcUser process(OidcUser oidcUser) {
    String sub = oidcUser.getSubject();
    String email = oidcUser.getEmail();
    Boolean emailVerified = oidcUser.getEmailVerified();

    if (sub == null || sub.isBlank()) {
      throw error("invalid_oauth_response", "Googleからの応答が不正です");
    }
    if (email == null || email.isBlank()) {
      throw error("email_not_available", "Googleアカウントからメールアドレスを取得できませんでした");
    }
    if (Boolean.FALSE.equals(emailVerified)) {
      throw error("email_not_verified", "確認済みのGoogleメールアドレスが必要です");
    }

    User appUser = findOrCreateUser(sub, email);

    if (appUser.isSuspended()) {
      throw error("account_suspended", "このアカウントは利用停止されています");
    }

    List<GrantedAuthority> authorities =
        List.of(new SimpleGrantedAuthority("ROLE_" + appUser.getRole().name()));
    return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), "email");
  }

  private User findOrCreateUser(String sub, String email) {
    var existingByProvider = users.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, sub);
    if (existingByProvider.isPresent()) {
      return existingByProvider.get();
    }

    if (users.existsByEmailIgnoreCase(email)) {
      // 既存のLOCAL(またはメール変更後に衝突した別)アカウントが存在する。
      // 乗っ取り防止のため自動統合はせず、明示的にログインを拒否する。
      throw error("account_exists", "このメールアドレスは既にEXレーダーに登録されています");
    }

    try {
      String randomPassword = encoder.encode(UUID.randomUUID().toString());
      User created = User.forGoogleSignup(email, sub, "新規ユーザー", randomPassword);
      return users.save(created);
    } catch (DataIntegrityViolationException e) {
      log.error("Googleアカウントでのユーザー登録に失敗しました", e);
      throw error("registration_failed", "アカウントの作成に失敗しました");
    }
  }

  private OAuth2AuthenticationException error(String code, String description) {
    return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
  }
}
