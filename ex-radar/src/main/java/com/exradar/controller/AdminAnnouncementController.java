package com.exradar.controller;

import com.exradar.dto.AdminUserSearchCriteria;
import com.exradar.form.AdminAnnouncementForm;
import com.exradar.repository.UserRepository;
import com.exradar.service.AdminAnnouncementService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 管理者からの「ログイン時お知らせ」画面。アクセス制御はSecurityConfigの/admin/**でADMINロールに
 * 限定している。ユーザー検索・セグメント抽出そのものはAdminUserSearchServiceをそのまま
 * 再利用し(検索ロジックの再実装はしない)、ここでは配信対象の確認とお知らせ本体の
 * 作成・編集・公開/非公開のみを担う。
 *
 * 「選択したユーザー」の複数選択パラメータ名はあえてselectedUserIdとし、
 * AdminUserSearchCriteria.userId()(検索条件側の「ユーザーID完全一致」の単一値フィールド)
 * とパラメータ名が衝突しないようにしている(運営メッセージ機能と同じ設計)。
 */
@Controller
@RequestMapping("/admin/announcements")
public class AdminAnnouncementController {
  private static final int PREVIEW_SAMPLE_SIZE = 10;

  private final AdminAnnouncementService service;
  private final UserRepository users;

  public AdminAnnouncementController(AdminAnnouncementService service, UserRepository users) {
    this.service = service;
    this.users = users;
  }

  @GetMapping
  String history(@RequestParam(defaultValue = "0") int page, Model model) {
    model.addAttribute("result", service.history(page));
    return "admin/announcements/list";
  }

  @GetMapping("/{id}")
  String detail(@PathVariable Long id, Model model) {
    model.addAttribute("announcement", service.detail(id));
    model.addAttribute("recipients", service.recipientsOf(id));
    return "admin/announcements/detail";
  }

  @GetMapping("/{id}/edit")
  String editForm(@PathVariable Long id, Model model) {
    var announcement = service.detail(id);
    if (!model.containsAttribute("adminAnnouncementForm")) {
      var form = new AdminAnnouncementForm();
      form.setTitle(announcement.getTitle());
      form.setBody(announcement.getBody());
      form.setLinkUrl(announcement.getLinkUrl());
      form.setStartsAt(announcement.getStartsAt());
      form.setEndsAt(announcement.getEndsAt());
      form.setPriority(announcement.getPriority());
      model.addAttribute("adminAnnouncementForm", form);
    }
    model.addAttribute("announcement", announcement);
    return "admin/announcements/edit";
  }

  @PostMapping("/{id}/edit")
  String editSubmit(
      @PathVariable Long id,
      @Valid @ModelAttribute("adminAnnouncementForm") AdminAnnouncementForm form,
      BindingResult result,
      Model model,
      RedirectAttributes redirect) {
    if (result.hasErrors()) {
      model.addAttribute("announcement", service.detail(id));
      return "admin/announcements/edit";
    }
    try {
      service.updateContent(
          id, form.getTitle(), form.getBody(), form.getLinkUrl(), form.getStartsAt(), form.getEndsAt(),
          form.getPriority() == null ? 0 : form.getPriority());
    } catch (IllegalArgumentException e) {
      model.addAttribute("dateError", e.getMessage());
      model.addAttribute("announcement", service.detail(id));
      return "admin/announcements/edit";
    }
    redirect.addFlashAttribute("success", "内容を更新しました");
    return "redirect:/admin/announcements/" + id;
  }

  @PostMapping("/{id}/publish")
  String publish(@PathVariable Long id, RedirectAttributes redirect) {
    service.publish(id);
    redirect.addFlashAttribute("success", "公開しました");
    return "redirect:/admin/announcements/" + id;
  }

  @PostMapping("/{id}/unpublish")
  String unpublish(@PathVariable Long id, RedirectAttributes redirect) {
    service.unpublish(id);
    redirect.addFlashAttribute("success", "非公開にしました");
    return "redirect:/admin/announcements/" + id;
  }

  /**
   * 配信対象の確定方法は2通り: (1) selectedUserId(複数可)が指定されていれば
   * 「選択したユーザー」、(2) 指定が無ければAdminUserSearchCriteriaを検索条件として扱い
   * 「現在の検索条件に一致する全ユーザー」。対象人数はここで一度確定させ表示する。
   */
  @GetMapping("/new")
  String composeForm(
      @RequestParam(required = false) List<Long> selectedUserId,
      @ModelAttribute AdminUserSearchCriteria criteria,
      Model model) {
    if (!model.containsAttribute("adminAnnouncementForm")) model.addAttribute("adminAnnouncementForm", new AdminAnnouncementForm());
    populateTargetModel(model, selectedUserId, criteria);
    return "admin/announcements/form";
  }

  /**
   * 対象は作成時点で固定: ここでtargetIdsを一度だけ確定させ(EXPLICITならその場のリスト、
   * CRITERIAならAdminUserSearchServiceを今この瞬間に評価した結果)、そのままRecipientの
   * 作成に使う。作成後に検索条件を保存して後から再評価する仕組みは無いため、既に確定した
   * 対象ユーザーは変化しない。
   */
  @PostMapping
  String create(
      Principal principal,
      @Valid @ModelAttribute("adminAnnouncementForm") AdminAnnouncementForm form,
      BindingResult result,
      @RequestParam String targetMode,
      @RequestParam(required = false) List<Long> selectedUserId,
      @ModelAttribute AdminUserSearchCriteria criteria,
      Model model,
      RedirectAttributes redirect) {
    boolean explicit = "EXPLICIT".equals(targetMode);
    List<Long> targetIds =
        explicit ? (selectedUserId == null ? List.of() : selectedUserId) : service.resolveTargetIdsForCriteria(criteria);

    if (result.hasErrors() || targetIds.isEmpty()) {
      if (targetIds.isEmpty()) model.addAttribute("targetError", "配信先が0人のため作成できません");
      populateTargetModelFrom(model, targetMode, targetIds, criteria);
      return "admin/announcements/form";
    }

    try {
      var announcement =
          service.create(
              form.getTitle(), form.getBody(), form.getLinkUrl(), form.getStartsAt(), form.getEndsAt(),
              form.getPriority() == null ? 0 : form.getPriority(), principal.getName(), targetIds);
      redirect.addFlashAttribute("success", targetIds.size() + "人を対象に作成しました(まだ非公開です)");
      return "redirect:/admin/announcements/" + announcement.getId();
    } catch (IllegalArgumentException e) {
      model.addAttribute("dateError", e.getMessage());
      populateTargetModelFrom(model, targetMode, targetIds, criteria);
      return "admin/announcements/form";
    }
  }

  private void populateTargetModel(Model model, List<Long> selectedUserId, AdminUserSearchCriteria criteria) {
    boolean explicit = selectedUserId != null && !selectedUserId.isEmpty();
    List<Long> targetIds = explicit ? selectedUserId : service.resolveTargetIdsForCriteria(criteria);
    populateTargetModelFrom(model, explicit ? "EXPLICIT" : "CRITERIA", targetIds, criteria);
  }

  private void populateTargetModelFrom(
      Model model, String targetMode, List<Long> targetIds, AdminUserSearchCriteria criteria) {
    model.addAttribute("targetMode", targetMode);
    model.addAttribute("criteria", criteria);
    model.addAttribute("targetUserIds", targetIds);
    model.addAttribute("targetCount", targetIds.size());
    var sampleIds = targetIds.stream().limit(PREVIEW_SAMPLE_SIZE).toList();
    model.addAttribute("targetSample", users.findAllById(sampleIds));
    model.addAttribute("targetSampleRemaining", Math.max(0, targetIds.size() - sampleIds.size()));
  }
}
