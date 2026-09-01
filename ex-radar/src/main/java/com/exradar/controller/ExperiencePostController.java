package com.exradar.controller;

import com.exradar.dto.ExperienceSearchCriteria;
import com.exradar.form.*;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.PersonalValueRepository;
import com.exradar.service.*;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/experiences")
public class ExperiencePostController {
  private final ExperiencePostService service;
  private final InteractionService interactions;
  private final CategoryRepository categories;
  private final PersonalValueRepository values;

  public ExperiencePostController(
      ExperiencePostService service,
      InteractionService interactions,
      CategoryRepository categories,
      PersonalValueRepository values) {
    this.service = service;
    this.interactions = interactions;
    this.categories = categories;
    this.values = values;
  }

  @ModelAttribute
  void categories(Model model) {
    model.addAttribute("categories", categories.findByActiveTrueOrderByDisplayOrder());
    model.addAttribute("personalValues", values.findAllByOrderByDisplayOrder());
  }

  @GetMapping
  String list(
      @ModelAttribute ExperienceSearchCriteria criteria,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "latest") String sort,
      Model model) {
    model.addAttribute("result", service.search(criteria, page, sort));
    model.addAttribute("sort", sort);
    return "experiences/list";
  }

  @GetMapping("/new")
  String createForm(Model model) {
    if (!model.containsAttribute("experiencePostForm"))
      model.addAttribute("experiencePostForm", new ExperiencePostForm());
    model.addAttribute("editing", false);
    return "experiences/form";
  }

  @GetMapping("/{id}/view-options")
  String viewOptions(@PathVariable Long id, Principal principal, Model model) {
    var email = principal == null ? null : principal.getName();
    if (!service.canReadExperiences(email)) return "redirect:/experiences/unlock";
    model.addAttribute("post", service.getVisible(id, email));
    return "experiences/view-options";
  }

  @GetMapping("/{id}/summary")
  String summary(@PathVariable Long id, Principal principal, Model model) {
    var email = principal == null ? null : principal.getName();
    if (!service.canReadExperiences(email)) return "redirect:/experiences/unlock";
    model.addAttribute("post", service.getVisible(id, email));
    return "experiences/summary";
  }

  @PostMapping
  String create(
      @Valid @ModelAttribute ExperiencePostForm form,
      BindingResult result,
      Principal principal,
      RedirectAttributes redirect) {
    if (result.hasErrors()) return "experiences/form";
    var post = service.create(form, principal.getName());
    redirect.addFlashAttribute("successMessage", "体験談を投稿しました");
    return "redirect:/experiences/" + post.getId();
  }

  @GetMapping("/{id}")
  String detail(@PathVariable Long id, Principal principal, Model model) {
    var email = principal == null ? null : principal.getName();
    if (!service.canReadExperiences(email)) return "redirect:/experiences/unlock";
    var post = service.getVisible(id, email);
    model.addAttribute("post", post);
    model.addAttribute("canManage", service.canManage(post, email));
    model.addAttribute(
        "canReact",
        email != null && !post.getAuthor().getEmail().equalsIgnoreCase(email));
    model.addAttribute("similarPosts", service.similar(post));
    if (post.isPublished()) {
      model.addAttribute("reactionSummary", interactions.reactions(id, email));
      model.addAttribute("comments", interactions.comments(id));
    }
    if (!model.containsAttribute("commentForm"))
      model.addAttribute("commentForm", new CommentForm());
    if (!model.containsAttribute("reportForm")) model.addAttribute("reportForm", new ReportForm());
    return "experiences/detail";
  }

  @GetMapping("/unlock")
  String unlock(Principal principal, Model model) {
    model.addAttribute("loggedIn", principal != null);
    model.addAttribute(
        "alreadyUnlocked", principal != null && service.canReadExperiences(principal.getName()));
    return "experiences/unlock";
  }

  @GetMapping("/{id}/edit")
  String editForm(@PathVariable Long id, Principal principal, Model model) {
    var post = service.getManageable(id, principal.getName());
    var form = ExperiencePostForm.from(post);
    form.setTagNames(String.join(", ", post.getTags().stream().map(t -> t.getName()).toList()));
    model.addAttribute("experiencePostForm", form);
    model.addAttribute("postId", id);
    model.addAttribute("editing", true);
    return "experiences/form";
  }

  @PostMapping("/{id}")
  String update(
      @PathVariable Long id,
      @Valid @ModelAttribute ExperiencePostForm form,
      BindingResult result,
      Principal principal,
      Model model,
      RedirectAttributes redirect) {
    if (result.hasErrors()) {
      model.addAttribute("postId", id);
      model.addAttribute("editing", true);
      return "experiences/form";
    }
    service.update(id, form, principal.getName());
    redirect.addFlashAttribute("successMessage", "体験談を更新しました");
    return "redirect:/experiences/" + id;
  }

  @PostMapping("/{id}/delete")
  String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
    service.delete(id, principal.getName());
    redirect.addFlashAttribute("successMessage", "体験談を削除しました");
    return "redirect:/mypage";
  }
}
