package com.exradar.controller;

import com.exradar.entity.*;
import com.exradar.form.*;
import com.exradar.service.InteractionService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class InteractionController {
  private final InteractionService service;

  public InteractionController(InteractionService service) {
    this.service = service;
  }

  @PostMapping("/experiences/{postId}/reactions/{type}")
  String reaction(
      @PathVariable Long postId,
      @PathVariable ReactionType type,
      Principal principal,
      RedirectAttributes redirect) {
    boolean added = service.toggleReaction(postId, type, principal.getName());
    redirect.addFlashAttribute("successMessage", added ? "リアクションを送りました" : "リアクションを取り消しました");
    return "redirect:/experiences/" + postId + "#reactions";
  }

  @PostMapping("/experiences/{postId}/comments")
  String comment(
      @PathVariable Long postId,
      @Valid @ModelAttribute CommentForm commentForm,
      BindingResult result,
      Principal principal,
      RedirectAttributes redirect) {
    if (result.hasErrors()) {
      redirect.addFlashAttribute(
          "org.springframework.validation.BindingResult.commentForm", result);
      redirect.addFlashAttribute("commentForm", commentForm);
      return "redirect:/experiences/" + postId + "#comments";
    }
    service.addComment(postId, commentForm, principal.getName());
    redirect.addFlashAttribute("successMessage", "コメントを投稿しました");
    return "redirect:/experiences/" + postId + "#comments";
  }

  @PostMapping("/experiences/{postId}/comments/{commentId}/delete")
  String deleteComment(
      @PathVariable Long postId,
      @PathVariable Long commentId,
      Principal principal,
      RedirectAttributes redirect) {
    service.deleteComment(postId, commentId, principal.getName());
    redirect.addFlashAttribute("successMessage", "コメントを削除しました");
    return "redirect:/experiences/" + postId + "#comments";
  }

  @PostMapping("/reports")
  String report(
      @Valid @ModelAttribute ReportForm reportForm,
      BindingResult result,
      Principal principal,
      RedirectAttributes redirect) {
    String destination = destination(reportForm);
    if (result.hasErrors()) {
      redirect.addFlashAttribute("org.springframework.validation.BindingResult.reportForm", result);
      redirect.addFlashAttribute("reportForm", reportForm);
      return destination;
    }
    service.report(reportForm, principal.getName());
    redirect.addFlashAttribute("successMessage", "通報を受け付けました");
    return destination;
  }

  private String destination(ReportForm form) {
    if (form.getTargetId() == null || form.getTargetId() < 1) return "redirect:/";
    return switch (form.getTargetType() == null
        ? ReportTargetType.EXPERIENCE_POST
        : form.getTargetType()) {
      case EXPERIENCE_POST -> "redirect:/experiences/" + form.getTargetId() + "#report";
      case COMMENT -> "redirect:/experiences/" + form.getTargetId() + "#comments";
    };
  }
}
