package com.exradar.controller;

import com.exradar.entity.Article;
import com.exradar.service.ArticleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** robots.txtとsitemap.xmlを実行時のホスト名から動的に生成する。公開記事(PUBLISHED)だけをsitemapに含める。 */
@RestController
public class SeoController {
  private final ArticleService articles;

  public SeoController(ArticleService articles) {
    this.articles = articles;
  }

  @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
  @ResponseBody
  String robots(HttpServletRequest request) {
    String base = baseUrl(request);
    return "User-agent: *\n"
        + "Disallow: /admin\n"
        + "Disallow: /api/admin\n"
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
