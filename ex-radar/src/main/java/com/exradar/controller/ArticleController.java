package com.exradar.controller;

import com.exradar.service.ArticleContentRenderer;
import com.exradar.service.ArticleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** 公開記事(SEO流入用)の一覧・詳細。未ログインでも閲覧できる。公開状態(PUBLISHED)の記事だけを表示する。 */
@Controller
@RequestMapping("/articles")
public class ArticleController {
  private final ArticleService service;
  private final ArticleContentRenderer renderer;

  public ArticleController(ArticleService service, ArticleContentRenderer renderer) {
    this.service = service;
    this.renderer = renderer;
  }

  @GetMapping
  String list(Model model) {
    model.addAttribute("articles", service.publishedList());
    return "articles/list";
  }

  @GetMapping("/{slug}")
  String detail(@PathVariable String slug, HttpServletRequest request, Model model) {
    var article = service.publishedBySlug(slug);
    model.addAttribute("article", article);
    model.addAttribute("contentHtml", renderer.render(article.getContent()));
    model.addAttribute(
        "canonicalUrl",
        ServletUriComponentsBuilder.fromContextPath(request).path("/articles/" + slug).toUriString());
    return "articles/detail";
  }
}
