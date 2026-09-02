package com.exradar.controller;

import com.exradar.form.*;
import com.exradar.service.AccountService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/mypage")
public class AccountController {
  private final AccountService service;

  public AccountController(AccountService s) {
    service = s;
  }

  @GetMapping
  public String index(Principal p, Model m) {
    var u = service.current(p.getName());
    m.addAttribute("user", u);
    m.addAttribute("posts", service.posts(p.getName()));
    m.addAttribute("drafts", service.drafts(p.getName()));
    m.addAttribute("reactions", service.reactions(p.getName()));
    return "mypage";
  }

  @GetMapping("/profile")
  public String profile(Principal p, Model m) {
    if (!m.containsAttribute("profileForm")) {
      var u = service.current(p.getName());
      var f = new ProfileForm();
      f.setDisplayName(u.getDisplayName());
      f.setAgeGroup(u.getAgeGroup());
      f.setEducation(u.getEducation());
      f.setOccupation(u.getOccupation());
      f.setPrefecture(u.getPrefecture());
      f.setBiography(u.getBiography());
      m.addAttribute(f);
    }
    return "account/profile";
  }

  @PostMapping("/profile")
  public String profileSave(
      Principal p,
      @Valid @ModelAttribute ProfileForm profileForm,
      BindingResult br,
      RedirectAttributes ra) {
    if (br.hasErrors()) return "account/profile";
    service.updateProfile(p.getName(), profileForm);
    ra.addFlashAttribute("success", "プロフィールを更新しました");
    return "redirect:/mypage/profile";
  }

  @GetMapping("/password")
  public String password(Model m) {
    if (!m.containsAttribute("passwordChangeForm")) m.addAttribute(new PasswordChangeForm());
    return "account/password";
  }

  @PostMapping("/password")
  public String passwordSave(
      Principal p,
      @Valid @ModelAttribute PasswordChangeForm passwordChangeForm,
      BindingResult br,
      RedirectAttributes ra) {
    if (br.hasErrors()) return "account/password";
    try {
      service.changePassword(p.getName(), passwordChangeForm);
    } catch (IllegalArgumentException e) {
      br.reject("password", e.getMessage());
      return "account/password";
    }
    ra.addFlashAttribute("success", "パスワードを変更しました");
    return "redirect:/mypage/password";
  }

  @GetMapping("/notifications")
  public String notifications(Principal p, Model m) {
    m.addAttribute("notifications", service.notifications(p.getName()));
    return "account/notifications";
  }

  @PostMapping("/notifications/{id}/read")
  public String read(Principal p, @PathVariable Long id) {
    service.read(p.getName(), id);
    return "redirect:/mypage/notifications";
  }

  @PostMapping("/notifications/read-all")
  public String readAll(Principal p) {
    service.readAll(p.getName());
    return "redirect:/mypage/notifications";
  }

  @PostMapping("/delete")
  public String delete(
      Principal p,
      @Valid @ModelAttribute DeleteAccountForm deleteAccountForm,
      BindingResult br,
      RedirectAttributes ra) {
    if (br.hasErrors()) {
      ra.addFlashAttribute("accountDeleteError", "現在のパスワードを入力してください");
      return "redirect:/mypage";
    }
    try {
      service.deleteAccount(p.getName(), deleteAccountForm.getPassword());
    } catch (IllegalArgumentException e) {
      ra.addFlashAttribute("accountDeleteError", e.getMessage());
      return "redirect:/mypage";
    }
    return "redirect:/login?deleted";
  }
}
