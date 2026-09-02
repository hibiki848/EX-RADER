package com.exradar.controller;

import com.exradar.exception.DuplicateEmailException;
import com.exradar.form.RegistrationForm;
import com.exradar.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
  private final UserService service;
  private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

  public AuthController(
      UserService service, ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
    this.service = service;
    this.clientRegistrations = clientRegistrations;
  }

  @ModelAttribute("googleLoginEnabled")
  boolean googleLoginEnabled() {
    return clientRegistrations.getIfAvailable() != null;
  }

  @GetMapping("/login")
  String login(CsrfToken csrfToken) {
    csrfToken.getToken();
    return "auth/login";
  }

  @GetMapping("/register")
  String register(Model model) {
    if (!model.containsAttribute("registrationForm"))
      model.addAttribute("registrationForm", new RegistrationForm());
    return "auth/register";
  }

  @PostMapping("/register")
  String register(
      @Valid @ModelAttribute RegistrationForm form,
      BindingResult result,
      RedirectAttributes redirect) {
    if (!form.getPassword().equals(form.getPasswordConfirmation()))
      result.rejectValue("passwordConfirmation", "mismatch", "確認用パスワードが一致しません");
    if (result.hasErrors()) return "auth/register";
    try {
      service.register(form);
    } catch (DuplicateEmailException e) {
      result.rejectValue("email", "duplicate", e.getMessage());
      return "auth/register";
    }
    redirect.addFlashAttribute("registered", true);
    return "redirect:/login";
  }
}
