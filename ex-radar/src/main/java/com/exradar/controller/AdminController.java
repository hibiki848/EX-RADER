package com.exradar.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.exradar.entity.Role;
import com.exradar.service.AdminService;

@Controller
@RequestMapping("/admin")
public class AdminController {
  // ブラウザ単位のアクセス解析除外Cookieは、ブラウザの上限(Chromeは400日)に合わせて
  // これ以上長くしても意味がない値を採用する。除外解除時はMaxAge=0で即座に失効させる。
  private static final Duration BROWSER_EXCLUSION_COOKIE_MAX_AGE = Duration.ofDays(400);

  private final AdminService service;

  public AdminController(AdminService service) {
    this.service = service;
  }

  @GetMapping
  public String dashboard(
      Principal principal,
      @RequestParam(defaultValue = "all") String filter,
      HttpServletRequest request,
      Model model) {
    model.addAttribute("userCount", service.userCount());
    model.addAttribute("adminCount", service.adminCount());
    model.addAttribute("suspendedUserCount", service.suspendedUserCount());
    model.addAttribute("publishedPostCount", service.publishedPostCount());
    model.addAttribute("users", service.users(filter));
    model.addAttribute("filter", filter);
    model.addAttribute("currentEmail", principal.getName());
    model.addAttribute("browserAnalyticsExcluded", NavigationAdvice.isBrowserExcluded(request));
    return "admin/dashboard";
  }

  @GetMapping("/users/{id}")
  public String userPosts(
      Principal principal, @PathVariable Long id, HttpServletRequest request, Model model) {
    model.addAttribute("userCount", service.userCount());
    model.addAttribute("adminCount", service.adminCount());
    model.addAttribute("suspendedUserCount", service.suspendedUserCount());
    model.addAttribute("publishedPostCount", service.publishedPostCount());
    model.addAttribute("users", service.users("all"));
    model.addAttribute("filter", "all");
    model.addAttribute("currentEmail", principal.getName());
    model.addAttribute("selectedUser", service.user(id));
    model.addAttribute("selectedUserPosts", service.posts(id));
    model.addAttribute("browserAnalyticsExcluded", NavigationAdvice.isBrowserExcluded(request));
    return "admin/dashboard";
  }

  /**
   * 「このブラウザをアクセス解析から除外」の切り替え。ログイン中ユーザーのDBフラグ
   * (analytics-exclusion、ユーザー単位)とは別物で、こちらはCookieでブラウザ単位に持たせる。
   * ログイン前(匿名)のアクセスも含めて、このブラウザからのGA4計測全体を止めたい場合に使う。
   * 管理者権限は/admin/**への既存のSecurityConfig設定でのみ保護しており、追加設定は不要。
   *
   * Cookie属性の選択理由:
   *  - HttpOnly: JS側で読み書きする必要が無く(このエンドポイントのPOSTのみで設定/解除する)、
   *    XSS経由での書き換えを防ぐため付与する。
   *  - Secure: 本番は常にHTTPSのため付与する(本番以外はGA4計測自体が無効なので実害はない)。
   *  - SameSite=Lax: 通常のページ遷移(GET)では送信され、外部サイトからのクロスサイト
   *    POST等では送られない標準的な設定。この用途では十分。
   *  - 値は常に"1"固定。除外解除はMaxAge=0で即時失効させ、Cookie自体を消す。
   */
  @PostMapping("/browser-analytics-exclusion")
  public String setBrowserAnalyticsExclusion(
      @RequestParam boolean excluded, HttpServletResponse response, RedirectAttributes redirect) {
    ResponseCookie cookie =
        ResponseCookie.from(NavigationAdvice.BROWSER_EXCLUSION_COOKIE, excluded ? "1" : "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(excluded ? BROWSER_EXCLUSION_COOKIE_MAX_AGE : Duration.ZERO)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    redirect.addFlashAttribute(
        "success",
        excluded ? "このブラウザをアクセス解析から除外しました" : "このブラウザをアクセス解析の対象に戻しました");
    return "redirect:/admin";
  }

  @PostMapping("/users/{id}/role")
  public String changeRole(
      Principal principal,
      @PathVariable Long id,
      @RequestParam Role role,
      RedirectAttributes redirect) {
    return perform(
        redirect,
        () -> service.changeRole(principal.getName(), id, role),
        "ユーザーの権限を更新しました");
  }

  @PostMapping("/users/{id}/suspension")
  public String changeSuspension(
      Principal principal,
      @PathVariable Long id,
      @RequestParam boolean suspended,
      RedirectAttributes redirect) {
    return perform(
        redirect,
        () -> service.setSuspended(principal.getName(), id, suspended),
        suspended ? "ユーザーを停止しました" : "ユーザーを利用可能に戻しました");
  }

  @PostMapping("/users/{id}/analytics-exclusion")
  public String changeAnalyticsExclusion(
      @PathVariable Long id, @RequestParam boolean excluded, RedirectAttributes redirect) {
    return perform(
        redirect,
        () -> service.setAnalyticsExcluded(id, excluded),
        excluded ? "アクセス解析の対象から除外しました" : "アクセス解析の対象に戻しました");
  }

  private String perform(RedirectAttributes redirect, Runnable action, String success) {
    try {
      action.run();
      redirect.addFlashAttribute("success", success);
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/admin";
  }
}
