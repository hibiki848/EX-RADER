package com.exradar.controller;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.exradar.entity.Role;
import com.exradar.service.AdminService;

@Controller
@RequestMapping("/admin")
public class AdminController {
  private final AdminService service;

  public AdminController(AdminService service) {
    this.service = service;
  }

  @GetMapping
  public String dashboard(
      Principal principal,
      @RequestParam(defaultValue = "all") String filter,
      Model model) {
    model.addAttribute("userCount", service.userCount());
    model.addAttribute("adminCount", service.adminCount());
    model.addAttribute("suspendedUserCount", service.suspendedUserCount());
    model.addAttribute("publishedPostCount", service.publishedPostCount());
    model.addAttribute("users", service.users(filter));
    model.addAttribute("filter", filter);
    model.addAttribute("currentEmail", principal.getName());
    return "admin/dashboard";
  }

  @GetMapping("/users/{id}")
  public String userPosts(
      Principal principal, @PathVariable Long id, Model model) {
    model.addAttribute("userCount", service.userCount());
    model.addAttribute("adminCount", service.adminCount());
    model.addAttribute("suspendedUserCount", service.suspendedUserCount());
    model.addAttribute("publishedPostCount", service.publishedPostCount());
    model.addAttribute("users", service.users("all"));
    model.addAttribute("filter", "all");
    model.addAttribute("currentEmail", principal.getName());
    model.addAttribute("selectedUser", service.user(id));
    model.addAttribute("selectedUserPosts", service.posts(id));
    return "admin/dashboard";
  }

  @PostMapping("/users/{id}/role")
  public String changeRole(
      Principal principal,
      @PathVariable Long id,
      @RequestParam Role role,
      RedirectAttributes redirect) {
    return perform(
        redirect,
        () -> service.changeRole(principal.getName(), id, role),
        "ユーザーの権限を更新しました");
  }

  @PostMapping("/users/{id}/suspension")
  public String changeSuspension(
      Principal principal,
      @PathVariable Long id,
      @RequestParam boolean suspended,
      RedirectAttributes redirect) {
    return perform(
        redirect,
        () -> service.setSuspended(principal.getName(), id, suspended),
        suspended ? "ユーザーを停止しました" : "ユーザーを利用可能に戻しました");
  }

  private String perform(RedirectAttributes redirect, Runnable action, String success) {
    try {
      action.run();
      redirect.addFlashAttribute("success", success);
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/admin";
  }
}
