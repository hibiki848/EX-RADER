package com.exradar.controller;

import com.exradar.entity.ReportStatus;
import com.exradar.service.AdminReportService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 通報管理画面。アクセス制御はSecurityConfigの/admin/**でADMINロールに限定しているのに加え、
 * AdminReportService側でも管理者であることを確認する(多層防御)。
 */
@Controller
@RequestMapping("/admin/reports")
public class AdminReportController {
  private final AdminReportService service;

  public AdminReportController(AdminReportService service) {
    this.service = service;
  }

  @GetMapping
  String list(Model model) {
    model.addAttribute("reports", service.list());
    return "admin/reports/list";
  }

  @PostMapping("/{id}/status")
  String changeStatus(
      @PathVariable Long id,
      @RequestParam ReportStatus status,
      Principal principal,
      RedirectAttributes redirect) {
    return perform(
        redirect, () -> service.changeStatus(id, status, principal.getName()), "ステータスを更新しました");
  }

  @PostMapping("/{id}/hide")
  String hide(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
    return perform(
        redirect, () -> service.hide(id, principal.getName()), "対象の体験談を非公開にしました");
  }

  @PostMapping("/{id}/delete")
  String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
    return perform(redirect, () -> service.delete(id, principal.getName()), "対象を削除しました");
  }

  private String perform(RedirectAttributes redirect, Runnable action, String success) {
    try {
      action.run();
      redirect.addFlashAttribute("success", success);
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/admin/reports";
  }
}
