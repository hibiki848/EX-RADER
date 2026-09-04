package com.exradar.controller;

import com.exradar.entity.ContactCategory;
import com.exradar.form.ContactInquiryForm;
import com.exradar.repository.UserRepository;
import com.exradar.security.ContactRateLimiter;
import com.exradar.service.ContactInquiryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * お問い合わせフォーム。未ログインでも送信できる(SecurityConfigで/contactをpermitAll)。
 * 正常送信後はPRG(POST→リダイレクト→GET)で完了メッセージを表示し、ブラウザの再読み込みによる
 * 二重登録を防ぐ。スパム対策はBean Validation・CSRF・XSS対策(th:text)に加え、
 * ContactRateLimiterによる送信元IPアドレスごとの簡易な短時間大量送信制限のみ
 * (外部CAPTCHAは導入せず、IPアドレス自体もDBへは保存しない)。
 */
@Controller
@RequestMapping("/contact")
public class ContactController {
  private final ContactInquiryService service;
  private final UserRepository users;
  private final ContactRateLimiter rateLimiter;

  public ContactController(ContactInquiryService service, UserRepository users, ContactRateLimiter rateLimiter) {
    this.service = service;
    this.users = users;
    this.rateLimiter = rateLimiter;
  }

  @GetMapping
  String form(Principal principal, HttpServletRequest request, Model model) {
    if (!model.containsAttribute("contactInquiryForm")) {
      var form = new ContactInquiryForm();
      if (principal != null) {
        users
            .findByEmailIgnoreCase(principal.getName())
            .ifPresent(
                u -> {
                  form.setName(u.getDisplayName());
                  form.setEmail(u.getEmail());
                });
      }
      model.addAttribute("contactInquiryForm", form);
    }
    populateFormOptions(model, request);
    return "contact";
  }

  @PostMapping
  String submit(
      Principal principal,
      @Valid @ModelAttribute("contactInquiryForm") ContactInquiryForm form,
      BindingResult result,
      HttpServletRequest request,
      Model model,
      RedirectAttributes redirect) {
    if (form.getRelatedPostId() != null && !service.postExists(form.getRelatedPostId())) {
      result.rejectValue("relatedPostId", "notFound", "指定された投稿が見つかりません");
    }

    if (!rateLimiter.allow(request.getRemoteAddr())) {
      model.addAttribute(
          "rateLimitError", "短時間に多くのお問い合わせが送信されています。しばらく時間をおいて再度お試しください。");
      populateFormOptions(model, request);
      return "contact";
    }
    if (result.hasErrors()) {
      populateFormOptions(model, request);
      return "contact";
    }

    service.submit(
        principal == null ? null : principal.getName(),
        form.getCategory(),
        form.getName(),
        form.getEmail(),
        form.getSubject(),
        form.getBody(),
        form.getRelatedPostId());
    redirect.addFlashAttribute("success", "お問い合わせを受け付けました。内容を確認のうえ、必要に応じてご連絡します。");
    return "redirect:/contact";
  }

  private void populateFormOptions(Model model, HttpServletRequest request) {
    model.addAttribute("categoryOptions", ContactCategory.values());
    model.addAttribute(
        "canonicalUrl", ServletUriComponentsBuilder.fromContextPath(request).path("/contact").toUriString());
  }
}
