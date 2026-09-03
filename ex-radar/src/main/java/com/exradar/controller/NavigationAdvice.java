package com.exradar.controller;

import com.exradar.repository.UserRepository;
import com.exradar.service.AccountService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@ControllerAdvice
public class NavigationAdvice {
  private final ObjectProvider<AccountService> service;
  private final ObjectProvider<UserRepository> users;
  private final Environment environment;

  @Value("${exradar.analytics.measurement-id:}")
  private String gaMeasurementId;

  // og:image用の共通デフォルト画像URL。未設定(空文字)の間はfragments/seo.htmlが
  // og:imageタグ自体を出力しないため、存在しない画像URLを生成することはない。
  @Value("${exradar.seo.default-og-image:}")
  private String defaultOgImage;

  // staging等でfalseにすると、fragments/seo.htmlがmeta robots(noindex等)を追加出力する。
  // 未設定時はtrue(=既存の本番仕様のまま)。
  @Value("${exradar.seo.indexable:true}")
  private boolean seoIndexable;

  public NavigationAdvice(
      ObjectProvider<AccountService> s, ObjectProvider<UserRepository> users, Environment environment) {
    service = s;
    this.users = users;
    this.environment = environment;
  }

  @ModelAttribute("unreadNotificationCount")
  public long unread(Principal p) {
    var a = service.getIfAvailable();
    return p == null || a == null ? 0 : a.unread(p.getName());
  }

  /**
   * このブラウザ自体をアクセス解析から除外する、ログイン状態と無関係なCookie。
   * ログイン前(匿名)のアクセスも除外したいという要望に対応するため、ログインユーザーの
   * DBフラグ(User#analyticsExcluded)とは別に、ブラウザ単位で保持する。
   * 値そのものに意味を持たせず存在有無だけで判定する(値は"1"固定)。
   */
  public static final String BROWSER_EXCLUSION_COOKIE = "exr_ga_excluded";

  /**
   * GA4タグ(gtag.js)を出力してよいかどうかをここで一元判定する。
   * fragments/analytics.htmlはgaMeasurementIdが空文字/nullなら何も出力しないため、
   * 「除外すべき状況では空文字を返す」だけで、ブラウザにGA4のスクリプトタグ自体が
   * 一切送られなくなる(クライアント側で計測後に除外する方式ではない)。
   *
   * 除外条件(いずれか1つでも該当すれば非計測):
   *  - prod以外のプロファイル(dev/test等)では、本番GA4へ誤送信しないよう常に無効化する
   *  - このブラウザにBROWSER_EXCLUSION_COOKIEが設定されている
   *    (ログイン状態を問わない。管理者が自分のPC・スマホを「このブラウザを除外」した場合)
   *  - ログイン中ユーザーが管理者(ROLE_ADMIN)、またはanalyticsExcluded=trueの場合
   */
  @ModelAttribute("gaMeasurementId")
  public String gaMeasurementId(Principal p, HttpServletRequest request) {
    if (gaMeasurementId == null || gaMeasurementId.isBlank()) return "";
    if (!environment.matchesProfiles("prod")) return "";
    if (isBrowserExcluded(request)) return "";
    if (p != null) {
      var repo = users.getIfAvailable();
      var excluded =
          repo != null
              && repo.findByEmailIgnoreCase(p.getName())
                  .map(u -> u.isExcludedFromAnalytics())
                  .orElse(false);
      if (excluded) return "";
    }
    return gaMeasurementId;
  }

  /**
   * public staticにしているのは、管理画面側(AdminController)が「このブラウザは現在
   * 除外されているか」をボタンの表示切り替えに使うため。判定ロジックの重複を避け、
   * Cookie名(BROWSER_EXCLUSION_COOKIE)ともどもここを唯一の定義元にする。
   */
  public static boolean isBrowserExcluded(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return false;
    for (Cookie cookie : cookies) {
      if (BROWSER_EXCLUSION_COOKIE.equals(cookie.getName())) {
        return "1".equals(cookie.getValue());
      }
    }
    return false;
  }

  @ModelAttribute("defaultOgImage")
  public String defaultOgImage() {
    return defaultOgImage;
  }

  @ModelAttribute("seoIndexable")
  public boolean seoIndexable() {
    return seoIndexable;
  }

  /** canonical・OGPのog:url組み立て用。末尾スラッシュなしのスキーム+ホスト(+ポート)。 */
  @ModelAttribute("baseUrl")
  public String baseUrl(HttpServletRequest request) {
    return ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
  }
}
