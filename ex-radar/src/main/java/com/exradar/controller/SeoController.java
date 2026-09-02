package com.exradar.controller;

import com.exradar.entity.Article;
import com.exradar.entity.PostStatus;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.service.ArticleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * robots.txtとsitemap.xmlを実行時のホスト名から動的に生成する。
 * URLはDB(記事・体験談・カテゴリ)に応じて変わるため、静的ファイルではなく動的生成が
 * この構成に適している。sitemapには公開状態のコンテンツのみを含め、下書き・非公開投稿・
 * ログイン後専用ページは一切含めない。
 *
 * 環境変数SEO_INDEXABLE(exradar.seo.indexable、デフォルトtrue)がfalseの場合は、
 * staging等の非公開検索環境として扱い、robots.txtは全面Disallowのみを返す
 * (hostnameでの判定は行わない。本番で未設定の場合に誤ってnoindexにならないよう、
 * 未設定時は必ずtrue=既存仕様のまま)。
 */
@RestController
public class SeoController {
  private final ArticleService articles;
  private final ExperiencePostRepository posts;
  private final CategoryRepository categories;

  @Value("${exradar.seo.indexable:true}")
  private boolean indexable;

  public SeoController(
      ArticleService articles, ExperiencePostRepository posts, CategoryRepository categories) {
    this.articles = articles;
    this.posts = posts;
    this.categories = categories;
  }

  @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
  @ResponseBody
  String robots(HttpServletRequest request) {
    if (!indexable) {
      return "User-agent: *\nDisallow: /\n";
    }
    String base = baseUrl(request);
    return "User-agent: *\n"
        + "Disallow: /admin\n"
        + "Disallow: /api/admin\n"
        + "Disallow: /mypage\n"
        + "Disallow: /insights\n"
        + "Disallow: /decision-memos\n"
        + "Disallow: /choices\n"
        + "Disallow: /login\n"
        + "Disallow: /register\n"
        + "Disallow: /oauth2\n"
        + "Disallow: /experiences/new\n"
        + "Disallow: /experiences/*/edit\n"
        + "Disallow: /experiences/*/draft\n"
        + "Disallow: /experiences/*/summary\n"
        + "Disallow: /experiences/*/view-options\n"
        + "Disallow: /experiences/unlock\n"
        + "\n"
        + "Sitemap: "
        + base
        + "/sitemap.xml\n";
  }

  @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
  @ResponseBody
  String sitemap(HttpServletRequest request) {
    String base = baseUrl(request);
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    xml.append(url(base, "/"));
    xml.append(url(base, "/articles"));
    for (Article article : articles.publishedList()) {
      xml.append(url(base, "/articles/" + article.getSlug()));
    }
    xml.append(url(base, "/experiences"));
    for (var category : categories.findByActiveTrueOrderByDisplayOrder()) {
      xml.append(url(base, "/experiences?categoryId=" + category.getId()));
    }
    // 公開済み(PUBLISHED)の体験談のみを含める。下書き(DRAFT)は絶対に含めない。
    for (var post : posts.findTop500ByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED)) {
      xml.append(url(base, "/experiences/" + post.getId()));
    }
    xml.append("</urlset>\n");
    return xml.toString();
  }

  private String url(String base, String path) {
    return "<url><loc>" + base + path + "</loc></url>\n";
  }

  private String baseUrl(HttpServletRequest request) {
    return ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
  }
}
