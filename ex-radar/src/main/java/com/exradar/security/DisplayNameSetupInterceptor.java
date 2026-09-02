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
 * 表示名が未設定(displayNamePending=true、Googleログイン直後)のユーザーが、
 * 表示名設定画面を経ずに他のページへ直接アクセスした場合、設定画面へ強制的に誘導する。
 *
 * UserRepositoryはObjectProviderで受け取る。HandlerInterceptorは@WebMvcTestが
 * (ステレオタイプに関わらず)自動検出してしまうため、UserRepositoryをモックしていない
 * 既存の各種スライステストのApplicationContext起動を壊さないようにするための措置。
 */
@Component
public class DisplayNameSetupInterceptor implements HandlerInterceptor {
  private final ObjectProvider<UserRepository> users;

  public DisplayNameSetupInterceptor(ObjectProvider<UserRepository> users) {
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
            .map(u -> u.isDisplayNamePending())
            .orElse(false);
    if (pending) {
      response.sendRedirect("/oauth2/display-name");
      return false;
    }
    return true;
  }
}
