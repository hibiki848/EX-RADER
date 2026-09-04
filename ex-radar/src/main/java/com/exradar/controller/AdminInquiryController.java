package com.exradar.controller;

import com.exradar.entity.InquiryStatus;
import com.exradar.service.ContactInquiryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 管理者向けお問い合わせ管理画面。アクセス制御はSecurityConfigの/admin/**でADMINロールに限定している。 */
@Controller
@RequestMapping("/admin/inquiries")
public class AdminInquiryController {
  private final ContactInquiryService service;

  public AdminInquiryController(ContactInquiryService service) {
    this.service = service;
  }

  @GetMapping
  String list(
      @RequestParam(required = false) InquiryStatus status,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    model.addAttribute("result", service.history(status, page));
    model.addAttribute("statusFilter", status);
    model.addAttribute("statusOptions", InquiryStatus.values());
    return "admin/inquiries/list";
  }

  @GetMapping("/{id}")
  String detail(@PathVariable Long id, Model model) {
    model.addAttribute("inquiry", service.detail(id));
    model.addAttribute("statusOptions", InquiryStatus.values());
    return "admin/inquiries/detail";
  }

  @PostMapping("/{id}/status")
  String changeStatus(@PathVariable Long id, @RequestParam InquiryStatus status, RedirectAttributes redirect) {
    service.changeStatus(id, status);
    redirect.addFlashAttribute("success", "ステータスを更新しました");
    return "redirect:/admin/inquiries/" + id;
  }

  @PostMapping("/{id}/memo")
  String updateMemo(
      @PathVariable Long id, @RequestParam(required = false) String memo, RedirectAttributes redirect) {
    service.updateAdminMemo(id, memo);
    redirect.addFlashAttribute("success", "メモを保存しました");
    return "redirect:/admin/inquiries/" + id;
  }
}
