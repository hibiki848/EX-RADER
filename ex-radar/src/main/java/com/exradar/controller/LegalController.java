package com.exradar.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 利用規約・プライバシーポリシー・お問い合わせ等、ユーザー登録の有無を問わず常に閲覧できる
 * 静的な規約・案内ページ。SecurityConfigで/terms・/privacy・/contactをpermitAllにしている。
 */
@Controller
public class LegalController {
  @GetMapping("/terms")
  String terms(HttpServletRequest request, Model model) {
    model.addAttribute("canonicalUrl", canonicalUrl(request, "/terms"));
    return "terms";
  }

  @GetMapping("/privacy")
  String privacy(HttpServletRequest request, Model model) {
    model.addAttribute("canonicalUrl", canonicalUrl(request, "/privacy"));
    return "privacy";
  }

  @GetMapping("/contact")
  String contact(HttpServletRequest request, Model model) {
    model.addAttribute("canonicalUrl", canonicalUrl(request, "/contact"));
    return "contact";
  }

  private String canonicalUrl(HttpServletRequest request, String path) {
    return ServletUriComponentsBuilder.fromContextPath(request).path(path).toUriString();
  }
}
