package com.exradar.controller;

import com.exradar.exception.ResourceNotFoundException;
import com.exradar.repository.*;
import com.exradar.service.ExperiencePostService;
import java.security.Principal;
import java.util.Comparator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/choices")
public class ChoiceGuideController {
  private final CategoryRepository categories;
  private final ExperiencePostRepository posts;
  private final UserRepository users;
  private final ExperiencePostService experienceService;

  public ChoiceGuideController(
      CategoryRepository c,
      ExperiencePostRepository p,
      UserRepository u,
      ExperiencePostService experienceService) {
    categories = c;
    posts = p;
    users = u;
    this.experienceService = experienceService;
  }

  @GetMapping
  public String index(Model m) {
    m.addAttribute("categories", categories.findByActiveTrueOrderByDisplayOrder());
    return "choices/list";
  }

  @GetMapping("/{slug}")
  public String detail(@PathVariable String slug, Principal principal, Model m) {
    if (principal == null || !experienceService.canReadExperiences(principal.getName()))
      return "redirect:/experiences/unlock";
    var category =
        categories.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("選択肢が見つかりません"));
    var experiences = posts.findByCategorySlugAndPublishedTrueOrderByCreatedAtDesc(slug);
    if (principal != null) {
      var names =
          users
              .findWithValuesByEmailIgnoreCase(principal.getName())
              .orElseThrow()
              .getValues()
              .stream()
              .map(v -> v.getName())
              .toList();
      experiences =
          experiences.stream()
              .sorted(
                  Comparator.comparingInt(
                      p -> -match(p.getLesson() + p.getWishKnown() + p.getLearned(), names)))
              .toList();
      m.addAttribute("selectedValueNames", names);
    }
    m.addAttribute("category", category);
    m.addAttribute("experiences", experiences);
    return "choices/detail";
  }

  private int match(String text, java.util.List<String> values) {
    if (text == null) return 0;
    return (int) values.stream().filter(text::contains).count();
  }
}
