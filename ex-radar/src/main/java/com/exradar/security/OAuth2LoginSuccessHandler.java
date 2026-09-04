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
 * 新規Googleユーザー(termsConsentPending)は規約同意画面を最優先、次に表示名が
 * 未設定(初回ログイン)なら表示名設定画面、それ以外はトップページへ遷移する。
 * 同意を表示名設定より先にするのは、TermsConsentInterceptorが同意未完了の間は
 * /oauth2/display-nameへのアクセスも同意画面へ差し戻すため、遷移順序をここでも
 * 合わせておくことで無駄なリダイレクトの往復を避けるため。
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
    var user = users.findByEmailIgnoreCase(authentication.getName());
    boolean consentPending = user.map(u -> u.isTermsConsentPending()).orElse(false);
    if (consentPending) {
      response.sendRedirect("/auth/consent");
      return;
    }
    boolean displayNamePending = user.map(u -> u.isDisplayNamePending()).orElse(false);
    response.sendRedirect(displayNamePending ? "/oauth2/display-name" : "/");
  }
}
