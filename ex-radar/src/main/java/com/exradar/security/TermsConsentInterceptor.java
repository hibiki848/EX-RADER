package com.exradar.security;

import com.exradar.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Google認証で「初めて」EXレーダーのユーザーになった人(User.termsConsentPending=true)が、
 * 同意画面(/auth/consent)を経ずに他のページへ直接アクセスした場合、同意画面へ強制的に誘導する
 * (DisplayNameSetupInterceptorと全く同じ考え方・構造)。termsConsentPendingは新規Google登録時
 * だけtrueで作られるため、termsAgreedAtがたまたまNULLなだけの既存ユーザーが誤って
 * 巻き込まれることはない。
 *
 * UserRepositoryはObjectProviderで受け取る。HandlerInterceptorは@WebMvcTestが
 * (ステレオタイプに関わらず)自動検出してしまうため、UserRepositoryをモックしていない
 * 既存の各種スライステストのApplicationContext起動を壊さないようにするための措置。
 */
@Component
public class TermsConsentInterceptor implements HandlerInterceptor {
  private final ObjectProvider<UserRepository> users;

  public TermsConsentInterceptor(ObjectProvider<UserRepository> users) {
    this.users = users;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
        || !auth.isAuthenticated()
        || auth instanceof AnonymousAuthenticationToken
        || auth.getName() == null) return true;

    UserRepository repository = users.getIfAvailable();
    if (repository == null) return true;

    boolean pending =
        repository
            .findByEmailIgnoreCase(auth.getName())
            .map(u -> u.isTermsConsentPending())
            .orElse(false);
    if (pending) {
      response.sendRedirect("/auth/consent");
      return false;
    }
    return true;
  }
}
