package com.exradar.controller;

import com.exradar.repository.UserRepository;
import com.exradar.service.AccountService;
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
   * GA4タグ(gtag.js)を出力してよいかどうかをここで一元判定する。
   * fragments/analytics.htmlはgaMeasurementIdが空文字/nullなら何も出力しないため、
   * 「除外すべき状況では空文字を返す」だけで、ブラウザにGA4のスクリプトタグ自体が
   * 一切送られなくなる(クライアント側で計測後に除外する方式ではない)。
   *
   * 除外条件:
   *  - prod以外のプロファイル(dev/test等)では、本番GA4へ誤送信しないよう常に無効化する
   *  - ログイン中ユーザーが管理者(ROLE_ADMIN)、またはanalyticsExcluded=trueの場合
   */
  @ModelAttribute("gaMeasurementId")
  public String gaMeasurementId(Principal p) {
    if (gaMeasurementId == null || gaMeasurementId.isBlank()) return "";
    if (!environment.matchesProfiles("prod")) return "";
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

  @ModelAttribute("defaultOgImage")
  public String defaultOgImage() {
    return defaultOgImage;
  }

  /** canonical・OGPのog:url組み立て用。末尾スラッシュなしのスキーム+ホスト(+ポート)。 */
  @ModelAttribute("baseUrl")
  public String baseUrl(HttpServletRequest request) {
    return ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
  }
}
