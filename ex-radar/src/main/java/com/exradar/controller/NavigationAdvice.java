package com.exradar.controller;

import com.exradar.entity.AdminAnnouncementRecipient;
import com.exradar.repository.UserRepository;
import com.exradar.service.AccountService;
import com.exradar.service.AdminAnnouncementService;
import com.exradar.service.AdminMessagingService;
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
  /** 同一ログインセッション中は原則1回のみモーダル表示するためのセッション属性キー。 */
  private static final String ANNOUNCEMENT_SHOWN_SESSION_KEY = "exr.announcementShown";

  private final ObjectProvider<AccountService> service;
  private final ObjectProvider<AdminMessagingService> adminMessaging;
  private final ObjectProvider<AdminAnnouncementService> announcements;
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
      ObjectProvider<AccountService> s,
      ObjectProvider<AdminMessagingService> adminMessaging,
      ObjectProvider<AdminAnnouncementService> announcements,
      ObjectProvider<UserRepository> users,
      Environment environment) {
    service = s;
    this.adminMessaging = adminMessaging;
    this.announcements = announcements;
    this.users = users;
    this.environment = environment;
  }

  /**
   * 既存の「通知」(コメント・リアクション、AccountService)と、新設の運営メッセージ
   * (AdminMessagingService)、両方の未読数を合算した値。バッジの表示先(ヘッダー・
   * モバイル下部ナビ)を1つに保つため、通知の種類ごとに別々のバッジを増やさず、
   * ここで一元的に合算する。匿名ユーザー(p==null)ではどちらのクエリも実行しない。
   */
  @ModelAttribute("unreadNotificationCount")
  public long unread(Principal p) {
    if (p == null) return 0;
    var a = service.getIfAvailable();
    var m = adminMessaging.getIfAvailable();
    long legacy = a == null ? 0 : a.unread(p.getName());
    long messages = m == null ? 0 : m.unreadCount(p.getName());
    return legacy + messages;
  }

  /**
   * ログイン後お知らせ(モーダル表示)。運営メッセージ(通知一覧に残るBOX型)とは別機能で、
   * 対象ユーザーがログイン後にページを表示した際、画面中央のモーダルとして表示する。
   *
   * - 匿名ユーザーでは呼ばない(unread()と同じ理由)。
   * - GET以外(POST等のPRGでリダイレクトのみ返す更新系アクション)や/api/**配下は、
   *   実際にHTMLを描画してユーザーへモーダルを見せるとは限らない(見せないままDBだけ
   *   「表示済み」にしてしまう事故を避ける)ため、ここで弾いて対象から外す。
   * - 同一HTTPセッション中に一度でも表示を選んだら、以後は(GETであっても)再チェックしない
   *   (session属性ANNOUNCEMENT_SHOWN_SESSION_KEY)。ページ遷移のたびにモーダルが
   *   出続けるUXを避け、「同一ログインセッション中は原則1回のみ表示」を満たす。
   *   ログアウトでセッションが破棄され、次回ログインでは新しいセッションになるため、
   *   次回ログイン時は自然に再度表示対象になる。
   * - 表示できるお知らせが実際に見つかった場合のみ、AdminAnnouncementService側で
   *   firstDisplayedAt/lastDisplayedAt/displayCountをその場でDBへ記録してから返す
   *   (JavaScript側だけで表示したことにする設計にはしない)。
   */
  @ModelAttribute("pendingAnnouncement")
  public AdminAnnouncementRecipient pendingAnnouncement(Principal p, HttpServletRequest request) {
    if (p == null) return null;
    if (!"GET".equals(request.getMethod())) return null;
    if (request.getRequestURI().startsWith("/api/")) return null;
    var session = request.getSession(false);
    if (session != null && Boolean.TRUE.equals(session.getAttribute(ANNOUNCEMENT_SHOWN_SESSION_KEY))) return null;

    var svc = announcements.getIfAvailable();
    if (svc == null) return null;
    var recipient = svc.pickAndRecordDisplayIfDue(p.getName());
    if (recipient == null) return null;
    request.getSession(true).setAttribute(ANNOUNCEMENT_SHOWN_SESSION_KEY, Boolean.TRUE);
    return recipient;
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
