package com.exradar.controller;

import com.exradar.form.PostRewardMilestoneForm;
import com.exradar.service.AdminRewardMilestoneService;
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

/** 管理者向け「投稿報酬設定」画面。既存のAdminAnnouncementController(list/new/edit)と同じ構成にしている。 */
@Controller
@RequestMapping("/admin/reward-milestones")
public class AdminRewardMilestoneController {
  private final AdminRewardMilestoneService service;

  public AdminRewardMilestoneController(AdminRewardMilestoneService service) {
    this.service = service;
  }

  @ModelAttribute
  void benefitOptions(Model model) {
    model.addAttribute("benefitOptions", service.benefitOptions());
  }

  @GetMapping
  String list(Model model) {
    model.addAttribute("milestones", service.list());
    return "admin/reward-milestones/list";
  }

  @GetMapping("/new")
  String newForm(Model model) {
    if (!model.containsAttribute("postRewardMilestoneForm"))
      model.addAttribute("postRewardMilestoneForm", new PostRewardMilestoneForm());
    return "admin/reward-milestones/form";
  }

  @PostMapping
  String create(
      @Valid @ModelAttribute PostRewardMilestoneForm form, BindingResult result, Model model, RedirectAttributes redirect) {
    if (result.hasErrors()) return "admin/reward-milestones/form";
    try {
      service.create(form.getRequiredPostCount(), form.getBenefitDefinitionId(), form.getRepeatInterval(), form.getDisplayOrder());
    } catch (IllegalArgumentException e) {
      model.addAttribute("formError", e.getMessage());
      return "admin/reward-milestones/form";
    }
    redirect.addFlashAttribute("success", "投稿報酬設定を作成しました");
    return "redirect:/admin/reward-milestones";
  }

  @GetMapping("/{id}/edit")
  String editForm(@PathVariable Long id, Model model) {
    var milestone = service.detail(id);
    if (!model.containsAttribute("postRewardMilestoneForm")) {
      var form = new PostRewardMilestoneForm();
      form.setRequiredPostCount(milestone.getRequiredPostCount());
      form.setBenefitDefinitionId(milestone.getBenefitDefinition().getId());
      form.setRepeatInterval(milestone.getRepeatInterval());
      form.setDisplayOrder(milestone.getDisplayOrder());
      model.addAttribute("postRewardMilestoneForm", form);
    }
    model.addAttribute("milestoneId", id);
    return "admin/reward-milestones/form";
  }

  @PostMapping("/{id}/edit")
  String editSubmit(
      @PathVariable Long id,
      @Valid @ModelAttribute PostRewardMilestoneForm form,
      BindingResult result,
      Model model,
      RedirectAttributes redirect) {
    if (result.hasErrors()) {
      model.addAttribute("milestoneId", id);
      return "admin/reward-milestones/form";
    }
    try {
      service.update(id, form.getRequiredPostCount(), form.getBenefitDefinitionId(), form.getRepeatInterval(), form.getDisplayOrder());
    } catch (IllegalArgumentException e) {
      model.addAttribute("formError", e.getMessage());
      model.addAttribute("milestoneId", id);
      return "admin/reward-milestones/form";
    }
    redirect.addFlashAttribute("success", "投稿報酬設定を更新しました");
    return "redirect:/admin/reward-milestones";
  }

  @PostMapping("/{id}/activate")
  String activate(@PathVariable Long id, RedirectAttributes redirect) {
    service.activate(id);
    redirect.addFlashAttribute("success", "有効にしました");
    return "redirect:/admin/reward-milestones";
  }

  @PostMapping("/{id}/deactivate")
  String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
    service.deactivate(id);
    redirect.addFlashAttribute("success", "無効にしました");
    return "redirect:/admin/reward-milestones";
  }
}
