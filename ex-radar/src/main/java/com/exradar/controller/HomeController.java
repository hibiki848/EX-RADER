package com.exradar.controller;

import com.exradar.service.ArticleService;
import com.exradar.service.ExperiencePostService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
  private final ExperiencePostService posts;
  private final ArticleService articles;

  public HomeController(ExperiencePostService posts, ArticleService articles) {
    this.posts = posts;
    this.articles = articles;
  }

  @GetMapping("/")
  String home(Model model) {
    model.addAttribute("latestPosts", posts.latest());
    model.addAttribute("recommendedPosts", posts.recommended());
    model.addAttribute("latestArticles", articles.latest());
    return "home";
  }
}
