package com.exradar.controller;

import com.exradar.exception.ResourceNotFoundException;
import com.exradar.repository.UserRepository;
import com.exradar.service.ExperiencePostService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PublicProfileController {
  private final UserRepository users;
  private final ExperiencePostService posts;

  public PublicProfileController(UserRepository users, ExperiencePostService posts) {
    this.users = users;
    this.posts = posts;
  }

  @GetMapping("/profiles/{id}")
  String profile(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      Principal principal,
      Model model) {
    var email = principal == null ? null : principal.getName();
    boolean wisdomUnlocked = posts.canReadExperiences(email);
    model.addAttribute(
        "profile",
        users.findById(id).orElseThrow(() -> new ResourceNotFoundException("投稿者が見つかりません")));
    model.addAttribute("result", posts.byAuthor(id, page, wisdomUnlocked));
    model.addAttribute("wisdomUnlocked", wisdomUnlocked);
    return "profiles/detail";
  }
}
