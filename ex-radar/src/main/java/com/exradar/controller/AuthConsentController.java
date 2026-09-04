package com.exradar.controller;

import com.exradar.form.TermsConsentForm;
import com.exradar.repository.UserRepository;
import com.exradar.service.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Google認証で「初めて」EXレーダーのユーザーになった人(User.termsConsentPending=true)専用の、
 * 利用規約・プライバシーポリシー同意画面。ログイン済み(Principalが存在する)ことだけを要求する。
 * TermsConsentInterceptorが、この画面・利用規約等の必要最低限のページを除くあらゆるページへの
 * アクセスをここへ強制的にリダイレクトする(URL直接入力等による同意回避を防ぐ)。
 */
@Controller
@RequestMapping("/auth/consent")
public class AuthConsentController {
  private final UserService service;
  private final UserRepository users;

  public AuthConsentController(UserService service, UserRepository users) {
    this.service = service;
    this.users = users;
  }

  @GetMapping
  String form(Model model) {
    if (!model.containsAttribute("termsConsentForm")) model.addAttribute("termsConsentForm", new TermsConsentForm());
    return "auth/consent";
  }

  @PostMapping
  String submit(
      Principal principal,
      @Valid @ModelAttribute("termsConsentForm") TermsConsentForm form,
      BindingResult result) {
    if (result.hasErrors()) return "auth/consent";
    service.completeTermsConsent(principal.getName());

    boolean displayNamePending =
        users.findByEmailIgnoreCase(principal.getName()).map(u -> u.isDisplayNamePending()).orElse(false);
    return displayNamePending ? "redirect:/oauth2/display-name" : "redirect:/";
  }
}
