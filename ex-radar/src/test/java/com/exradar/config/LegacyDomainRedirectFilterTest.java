package com.exradar.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link LegacyDomainRedirectFilter}の単体テスト。Spring起動は不要なため、
 * request/response/chainをMockitoで直接組み立てて検証する。
 *
 * <p>request.getServerName()はserver.forward-headers-strategy=frameworkにより
 * X-Forwarded-Host反映後の値になっている前提(NavigationAdvice#baseUrlと同じ前提)のため、
 * このテストでもX-Forwarded-Hostヘッダーそのものではなく、getServerName()の戻り値を直接スタブする。
 */
@ExtendWith(MockitoExtension.class)
class LegacyDomainRedirectFilterTest {
  @Mock HttpServletRequest request;
  @Mock HttpServletResponse response;
  @Mock FilterChain chain;

  @Test
  void redirectsLegacyHostRootTo301OnCanonicalHost() throws Exception {
    var filter = new LegacyDomainRedirectFilter("ex-rader-production.up.railway.app", "ex-radar.jp");
    when(request.getServerName()).thenReturn("ex-rader-production.up.railway.app");
    when(request.getRequestURI()).thenReturn("/");
    when(request.getQueryString()).thenReturn(null);

    filter.doFilter(request, response, chain);

    verify(response).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
    verify(response).setHeader("Location", "https://ex-radar.jp/");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void redirectsArbitraryPathToSamePathOnCanonicalHost() throws Exception {
    var filter = new LegacyDomainRedirectFilter("ex-rader-production.up.railway.app", "ex-radar.jp");
    when(request.getServerName()).thenReturn("ex-rader-production.up.railway.app");
    when(request.getRequestURI()).thenReturn("/articles/example");
    when(request.getQueryString()).thenReturn(null);

    filter.doFilter(request, response, chain);

    verify(response).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
    verify(response).setHeader("Location", "https://ex-radar.jp/articles/example");
  }

  @Test
  void preservesQueryStringWhenRedirecting() throws Exception {
    var filter = new LegacyDomainRedirectFilter("ex-rader-production.up.railway.app", "ex-radar.jp");
    when(request.getServerName()).thenReturn("ex-rader-production.up.railway.app");
    when(request.getRequestURI()).thenReturn("/experiences");
    when(request.getQueryString()).thenReturn("categoryId=3");

    filter.doFilter(request, response, chain);

    verify(response).setHeader("Location", "https://ex-radar.jp/experiences?categoryId=3");
  }

  @Test
  void doesNotRedirectRequestsAlreadyOnCanonicalHost() throws Exception {
    var filter = new LegacyDomainRedirectFilter("ex-rader-production.up.railway.app", "ex-radar.jp");
    when(request.getServerName()).thenReturn("ex-radar.jp");
    when(request.getRequestURI()).thenReturn("/");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
  }

  @Test
  void doesNotRedirectUnrelatedStagingHost() throws Exception {
    var filter = new LegacyDomainRedirectFilter("ex-rader-production.up.railway.app", "ex-radar.jp");
    when(request.getServerName()).thenReturn("ex-radar-staging.up.railway.app");
    when(request.getRequestURI()).thenReturn("/");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
  }

  @Test
  void doesNotRedirectLocalhost() throws Exception {
    var filter = new LegacyDomainRedirectFilter("ex-rader-production.up.railway.app", "ex-radar.jp");
    when(request.getServerName()).thenReturn("localhost");
    when(request.getRequestURI()).thenReturn("/");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
  }

  @Test
  void isCompletelyDisabledWhenLegacyHostIsNotConfigured() throws Exception {
    var filter = new LegacyDomainRedirectFilter("", "ex-radar.jp");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
    // legacyHost未設定時はホスト判定に入る前に抜けるため、getServerName()もgetRequestURI()も呼ばれない
    verify(request, never()).getServerName();
    verify(request, never()).getRequestURI();
  }

  /**
   * OAuth2の認可開始・コールバックは、往復の途中でホストを跨ぐとセッション(state)を
   * 見失いログインに失敗するため、旧ドメイン上でもリダイレクトせずそのまま処理させる。
   */
  @Test
  void doesNotRedirectOAuthAuthorizationOrCallbackPathsEvenOnLegacyHost() throws Exception {
    var filter = new LegacyDomainRedirectFilter("ex-rader-production.up.railway.app", "ex-radar.jp");
    when(request.getRequestURI()).thenReturn("/oauth2/authorization/google");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
  }

  @Test
  void doesNotRedirectOAuthCallbackPathEvenOnLegacyHost() throws Exception {
    var filter = new LegacyDomainRedirectFilter("ex-rader-production.up.railway.app", "ex-radar.jp");
    when(request.getRequestURI()).thenReturn("/login/oauth2/code/google");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
  }

  @Test
  void hostComparisonIsCaseInsensitive() throws Exception {
    var filter = new LegacyDomainRedirectFilter("ex-rader-production.up.railway.app", "ex-radar.jp");
    when(request.getServerName()).thenReturn("EX-RADER-PRODUCTION.UP.RAILWAY.APP");
    when(request.getRequestURI()).thenReturn("/");
    when(request.getQueryString()).thenReturn(null);

    filter.doFilter(request, response, chain);

    verify(response).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
  }

  @Test
  void tolerates_schemeAndPathAccidentallyIncludedInConfiguredHostValues() throws Exception {
    // 環境変数の入力ミス("https://..."やパス付き)でも比較がずれないことを確認する
    var filter =
        new LegacyDomainRedirectFilter(
            "https://ex-rader-production.up.railway.app/", "https://ex-radar.jp");
    when(request.getServerName()).thenReturn("ex-rader-production.up.railway.app");
    when(request.getRequestURI()).thenReturn("/");
    when(request.getQueryString()).thenReturn(null);

    filter.doFilter(request, response, chain);

    verify(response).setHeader("Location", "https://ex-radar.jp/");
  }
}
