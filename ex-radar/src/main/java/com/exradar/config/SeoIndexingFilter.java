package com.exradar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 環境変数SEO_INDEXABLE(exradar.seo.indexable、デフォルトtrue)がfalseの間、
 * 全HTTPレスポンスへ X-Robots-Tag: noindex, nofollow, noarchive を付与する。
 * robots.txtやmeta robotsタグに気づかない検索エンジン・クローラーがいても、
 * HTTPヘッダーだけでインデックスを確実に防げるようにするための多重の安全策。
 * 未設定(=true)の本番では常に何も付与しない。
 */
@Component
public class SeoIndexingFilter extends OncePerRequestFilter {
  private final boolean indexable;

  public SeoIndexingFilter(@Value("${exradar.seo.indexable:true}") boolean indexable) {
    this.indexable = indexable;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!indexable) {
      response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    }
    filterChain.doFilter(request, response);
  }

  /** エラーページ(/errorへのフォワード)にもX-Robots-Tagを付与するため、既定のスキップを無効化する。 */
  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }
}
