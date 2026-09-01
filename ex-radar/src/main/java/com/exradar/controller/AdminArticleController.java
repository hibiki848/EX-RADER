package com.exradar.controller;

import com.exradar.exception.DuplicateSlugException;
import com.exradar.form.ArticleForm;
import com.exradar.service.ArticleService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 管理者の記事管理画面。アクセス制御はSecurityConfigの/admin/**でADMINロールに限定している。 */
@Controller
@RequestMapping("/admin/articles")
public class AdminArticleController {
  private final ArticleService service;

  public AdminArticleController(ArticleService service) {
    this.service = service;
  }

  @GetMapping
  String list(Model model) {
    model.addAttribute("articles", service.all());
    return "admin/articles/list";
  }

  @GetMapping("/new")
  String createForm(Model model) {
    if (!model.containsAttribute("articleForm")) model.addAttribute("articleForm", new ArticleForm());
    model.addAttribute("editing", false);
    return "admin/articles/form";
  }

  @PostMapping
  String create(
      @Valid @ModelAttribute("articleForm") ArticleForm form,
      BindingResult result,
      Model model,
      RedirectAttributes redirect) {
    if (result.hasErrors()) {
      model.addAttribute("editing", false);
      return "admin/articles/form";
    }
    try {
      service.create(form);
    } catch (DuplicateSlugException e) {
      result.rejectValue("slug", "duplicate", e.getMessage());
      model.addAttribute("editing", false);
      return "admin/articles/form";
    }
    redirect.addFlashAttribute("success", "記事を下書きとして作成しました");
    return "redirect:/admin/articles";
  }

  @GetMapping("/{id}/edit")
  String editForm(@PathVariable Long id, Model model) {
    var article = service.find(id);
    if (!model.containsAttribute("articleForm"))
      model.addAttribute("articleForm", ArticleForm.from(article));
    model.addAttribute("article", article);
    model.addAttribute("editing", true);
    return "admin/articles/form";
  }

  @PostMapping("/{id}")
  String update(
      @PathVariable Long id,
      @Valid @ModelAttribute("articleForm") ArticleForm form,
      BindingResult result,
      Model model,
      RedirectAttributes redirect) {
    if (result.hasErrors()) {
      model.addAttribute("article", service.find(id));
      model.addAttribute("editing", true);
      return "admin/articles/form";
    }
    try {
      service.update(id, form);
    } catch (DuplicateSlugException e) {
      result.rejectValue("slug", "duplicate", e.getMessage());
      model.addAttribute("article", service.find(id));
      model.addAttribute("editing", true);
      return "admin/articles/form";
    }
    redirect.addFlashAttribute("success", "記事を更新しました");
    return "redirect:/admin/articles";
  }

  @PostMapping("/{id}/publish")
  String publish(@PathVariable Long id, RedirectAttributes redirect) {
    service.publish(id);
    redirect.addFlashAttribute("success", "記事を公開しました");
    return "redirect:/admin/articles";
  }

  @PostMapping("/{id}/unpublish")
  String unpublish(@PathVariable Long id, RedirectAttributes redirect) {
    service.unpublish(id);
    redirect.addFlashAttribute("success", "記事を非公開にしました");
    return "redirect:/admin/articles";
  }

  @PostMapping("/{id}/delete")
  String delete(@PathVariable Long id, RedirectAttributes redirect) {
    service.delete(id);
    redirect.addFlashAttribute("success", "記事を削除しました");
    return "redirect:/admin/articles";
  }
}
