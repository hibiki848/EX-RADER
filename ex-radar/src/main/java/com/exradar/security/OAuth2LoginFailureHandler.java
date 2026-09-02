package com.exradar.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Googleログイン失敗時、内部エラー情報(例外メッセージ・Client Secret等)を一切画面へ出さず、
 * 固定の内部パス(/login)へ理由コードだけ付けてリダイレクトする(Open Redirect対策として遷移先は固定)。
 * 個別メッセージはlogin.html側でクエリの種類に応じて表示する。
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {
  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    String code = "oauth_failed";
    if (exception instanceof OAuth2AuthenticationException oauthException
        && oauthException.getError() != null) {
      String errorCode = oauthException.getError().getErrorCode();
      if (errorCode != null) {
        switch (errorCode) {
          case "account_exists" -> code = "oauth_account_exists";
          case "account_suspended" -> code = "oauth_suspended";
          case "email_not_available", "email_not_verified" -> code = "oauth_email";
          default -> code = "oauth_failed";
        }
      }
    }
    response.sendRedirect("/login?error=" + code);
  }
}
