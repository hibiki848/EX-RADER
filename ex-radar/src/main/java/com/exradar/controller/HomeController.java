package com.exradar.controller;

import com.exradar.service.ArticleService;
import com.exradar.service.ExperiencePostService;
import java.security.Principal;
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
  String home(Principal principal, Model model) {
    var email = principal == null ? null : principal.getName();
    boolean wisdomUnlocked = posts.canReadExperiences(email);
    model.addAttribute("wisdomUnlocked", wisdomUnlocked);
    model.addAttribute("latestPosts", posts.latest(wisdomUnlocked));
    model.addAttribute("recommendedPosts", posts.recommended(wisdomUnlocked));
    model.addAttribute("latestArticles", articles.latest());
    return "home";
  }
}
