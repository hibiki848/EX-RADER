package com.exradar.controller;

import com.exradar.exception.ResourceNotFoundException;
import com.exradar.form.LinkAccountForm;
import com.exradar.repository.UserRepository;
import com.exradar.security.OAuth2LoginFailureHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Googleログインのメールアドレスが既存アカウント(通常はLOCAL)と一致した場合の連携確認画面。
 * メールアドレスの一致だけでは連携せず、既存アカウントのパスワードで本人確認したうえで
 * Googleアカウントを紐付ける。連携後もメールアドレス+パスワードでのログインは引き続き使える。
 */
@Controller
public class AccountLinkController {
  private static final long PENDING_LINK_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);

  private final UserRepository users;
  private final AuthenticationManager authenticationManager;

  public AccountLinkController(UserRepository users, AuthenticationManager authenticationManager) {
    this.users = users;
    this.authenticationManager = authenticationManager;
  }

  @GetMapping("/oauth2/link-account")
  String form(HttpServletRequest request, Model model) {
    String email = pendingEmail(request);
    if (email == null) return "redirect:/login";
    model.addAttribute("email", email);
    if (!model.containsAttribute("linkAccountForm"))
      model.addAttribute("linkAccountForm", new LinkAccountForm());
    return "auth/link-account";
  }

  @PostMapping("/oauth2/link-account")
  String submit(
      HttpServletRequest request,
      HttpServletResponse response,
      @Valid @ModelAttribute LinkAccountForm form,
      BindingResult result,
      Model model) {
    String email = pendingEmail(request);
    HttpSession session = request.getSession(false);
    String sub =
        session == null
            ? null
            : (String) session.getAttribute(OAuth2LoginFailureHandler.SESSION_ATTR_SUB);
    if (email == null || sub == null) return "redirect:/login";

    if (result.hasErrors()) {
      model.addAttribute("email", email);
      return "auth/link-account";
    }

    Authentication authResult;
    try {
      authResult =
          authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(email, form.getPassword()));
    } catch (DisabledException e) {
      model.addAttribute("email", email);
      model.addAttribute("linkError", "このアカウントは利用停止されています。");
      return "auth/link-account";
    } catch (AuthenticationException e) {
      model.addAttribute("email", email);
      model.addAttribute("linkError", "パスワードが正しくありません。");
      return "auth/link-account";
    }

    var user =
        users
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResourceNotFoundException("ユーザーが見つかりません"));
    user.linkGoogleAccount(sub);
    try {
      // findByEmailIgnoreCase()で取得した時点でトランザクションが終わり、userはdetached状態になっている。
      // save()を呼ばない限りlinkGoogleAccount()によるprovider_user_idの変更はDBへ反映されず、
      // 次回のGoogleログインで再びアカウント連携確認画面に戻ってしまう(このメソッドの主目的)。
      users.save(user);
    } catch (DataIntegrityViolationException e) {
      // provider_user_id(Googleのsub)には一意制約があるため、この10分間の待機中に
      // 同じGoogleアカウントが別のEXレーダーアカウントへ連携済みになっていた場合はここで検知する。
      model.addAttribute("email", email);
      model.addAttribute("linkError", "このGoogleアカウントは既に別のアカウントと連携されています。");
      return "auth/link-account";
    }

    // 認証成功。ここから先はこのリクエストを正式なログイン状態にする(プログラムによるログイン)。
    // セッション固定攻撃対策として、認証確定後にセッションIDを再発行してからSecurityContextを保存する。
    request.changeSessionId();
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authResult);
    SecurityContextHolder.setContext(context);
    new HttpSessionSecurityContextRepository().saveContext(context, request, response);

    HttpSession newSession = request.getSession(false);
    if (newSession != null) {
      newSession.removeAttribute(OAuth2LoginFailureHandler.SESSION_ATTR_SUB);
      newSession.removeAttribute(OAuth2LoginFailureHandler.SESSION_ATTR_EMAIL);
      newSession.removeAttribute(OAuth2LoginFailureHandler.SESSION_ATTR_ISSUED_AT);
    }

    return "redirect:/";
  }

  /** セッションに保存された連携待ちメールアドレスを返す。無い、または期限切れならnull。 */
  private String pendingEmail(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) return null;
    Object issuedAt = session.getAttribute(OAuth2LoginFailureHandler.SESSION_ATTR_ISSUED_AT);
    if (!(issuedAt instanceof Long issuedAtMillis)) return null;
    if (System.currentTimeMillis() - issuedAtMillis > PENDING_LINK_TTL_MILLIS) return null;
    Object email = session.getAttribute(OAuth2LoginFailureHandler.SESSION_ATTR_EMAIL);
    return email instanceof String s ? s : null;
  }
}
