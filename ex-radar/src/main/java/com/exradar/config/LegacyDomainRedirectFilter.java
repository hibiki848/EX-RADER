package com.exradar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 旧Railway本番ホスト({@code legacyHost})宛のリクエストのみを、独自ドメイン
 * ({@code canonicalHost})へ301(Moved Permanently)でリダイレクトする。
 *
 * <p>ホスト名の完全一致でのみ発火するため、legacyHostが未設定(dev/test/staging等)や、
 * 一致しないホスト(ex-radar.jp本体、localhost、Railwayのプライベートネットワーク経由の
 * ヘルスチェック等、公開カスタムドメインを経由しないアクセス)には一切影響しない。
 *
 * <p>server.forward-headers-strategy=framework により、HttpServletRequestが返す
 * scheme/host/portは既にX-Forwarded-*ヘッダー反映後の値になっている
 * (NavigationAdvice#baseUrlが同じ前提でcanonical URLを組み立てているのと同じ仕組みを再利用)。
 * そのためこのフィルターは素のHostヘッダーではなくrequest.getServerName()を見ればよい。
 *
 * <p>OAuth2の認可リクエスト開始({@code /oauth2/authorization/**})とコールバック
 * ({@code /login/oauth2/code/**})だけは対象から除外する。Googleへ送るredirect_uriは
 * リクエスト時点のホストから動的に組み立てられ、認可状態(state)はHTTPセッション
 * (ドメインに紐づくCookie)に保存されるため、往復の途中でホストを跨ぐとセッションが
 * 見つからずログインに失敗する。旧ドメイン向けのGoogle OAuthリダイレクトURIも
 * Google Cloud側に残っているため、この2パスだけは旧ドメイン上でそのまま完結させる
 * (完了後の遷移先である"/"等は通常どおりリダイレクト対象になる)。
 */
public class LegacyDomainRedirectFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(LegacyDomainRedirectFilter.class);

  private final String legacyHost;
  private final String canonicalHost;

  public LegacyDomainRedirectFilter(String legacyHost, String canonicalHost) {
    // 環境変数の入力ミスで先頭に"https://"やパスが混入していても比較がずれないよう軽く正規化する。
    this.legacyHost = normalizeHost(legacyHost);
    this.canonicalHost = normalizeHost(canonicalHost);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (legacyHost.isEmpty()) return true; // 未設定の環境(dev/test/staging等)では常に素通り
    String uri = request.getRequestURI();
    return uri.startsWith("/oauth2/authorization/") || uri.startsWith("/login/oauth2/code/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (legacyHost.equalsIgnoreCase(request.getServerName())) {
      String query = request.getQueryString();
      String location =
          "https://" + canonicalHost + request.getRequestURI() + (query == null ? "" : "?" + query);
      log.info("Legacy domain redirect: {} -> {}", request.getServerName() + request.getRequestURI(), location);
      response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
      response.setHeader("Location", location);
      return;
    }
    chain.doFilter(request, response);
  }

  private static String normalizeHost(String value) {
    if (value == null) return "";
    String host = value.trim();
    host = host.replaceFirst("^https?://", "");
    int slash = host.indexOf('/');
    if (slash >= 0) host = host.substring(0, slash);
    return host;
  }
}
