package com.exradar.security;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * Googleログインのメールアドレスと同じメールアドレスのEXレーダーアカウント(通常はLOCAL)が
 * 既に存在する場合にスローされる。メールアドレスが一致しただけで自動連携はせず、
 * OAuth2LoginFailureHandlerがこの例外を捕捉して「パスワードを確認して連携する」画面
 * (/oauth2/link-account)へ誘導するために使う。
 */
public class AccountLinkRequiredException extends OAuth2AuthenticationException {
  private final String googleSub;
  private final String email;

  public AccountLinkRequiredException(String googleSub, String email) {
    super(new OAuth2Error("link_required", "既存アカウントとの連携確認が必要です", null));
    this.googleSub = googleSub;
    this.email = email;
  }

  public String getGoogleSub() {
    return googleSub;
  }

  public String getEmail() {
    return email;
  }
}
