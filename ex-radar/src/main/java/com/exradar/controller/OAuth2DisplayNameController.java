package com.exradar.controller;

import com.exradar.form.DisplayNameForm;
import com.exradar.service.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Googleアカウントでの初回ログイン後、EXレーダー上の表示名(Googleの本名とは独立)を
 * ユーザー自身に設定してもらう画面。ログイン済み(Principalが存在する)ことだけを要求する。
 */
@Controller
public class OAuth2DisplayNameController {
  private final UserService service;

  public OAuth2DisplayNameController(UserService service) {
    this.service = service;
  }

  @GetMapping("/oauth2/display-name")
  String form(Model model) {
    if (!model.containsAttribute("displayNameForm"))
      model.addAttribute("displayNameForm", new DisplayNameForm());
    return "auth/display-name";
  }

  @PostMapping("/oauth2/display-name")
  String submit(
      Principal principal,
      @Valid @ModelAttribute DisplayNameForm form,
      BindingResult result) {
    if (result.hasErrors()) return "auth/display-name";
    service.completeDisplayNameSetup(principal.getName(), form.getDisplayName());
    return "redirect:/";
  }
}
