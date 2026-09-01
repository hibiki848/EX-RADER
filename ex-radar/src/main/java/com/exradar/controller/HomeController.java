package com.exradar.controller;

import com.exradar.service.ExperiencePostService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
  private final ExperiencePostService posts;

  public HomeController(ExperiencePostService posts) {
    this.posts = posts;
  }

  @GetMapping("/")
  String home(Model model) {
    model.addAttribute("latestPosts", posts.latest());
    model.addAttribute("recommendedPosts", posts.recommended());
    return "home";
  }
}
