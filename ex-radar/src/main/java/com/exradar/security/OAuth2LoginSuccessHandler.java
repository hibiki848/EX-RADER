package com.exradar.security;

import com.exradar.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Googleログイン成功後の遷移先を決める。リダイレクト先は固定の内部パスのみで、
 * 外部から指定できる値は一切使わないためOpen Redirectは発生しない。
 * 表示名が未設定(初回ログイン)の場合は表示名設定画面へ、それ以外はトップページへ遷移する。
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
  private final UserRepository users;

  public OAuth2LoginSuccessHandler(UserRepository users) {
    this.users = users;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    boolean pending =
        users
            .findByEmailIgnoreCase(authentication.getName())
            .map(u -> u.isDisplayNamePending())
            .orElse(false);
    response.sendRedirect(pending ? "/oauth2/display-name" : "/");
  }
}
