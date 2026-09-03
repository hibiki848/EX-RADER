package com.exradar.controller;

import com.exradar.dto.AdminUserSearchCriteria;
import com.exradar.form.AdminMessageForm;
import com.exradar.repository.UserRepository;
import com.exradar.service.AdminMessagingService;
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
 * 管理者からのメッセージ配信画面。アクセス制御はSecurityConfigの/admin/**でADMINロールに
 * 限定している。ユーザー検索・セグメント抽出そのものはAdminUserSearchServiceを
 * そのまま再利用し(検索ロジックの再実装はしない)、ここでは配信対象の確認と
 * メッセージ本体の作成・配信のみを担う。
 *
 * 「選択したユーザー」の複数選択パラメータ名はあえてselectedUserIdとし、
 * AdminUserSearchCriteria.userId()(検索条件側の「ユーザーID完全一致」の単一値フィールド)
 * とパラメータ名が衝突しないようにしている(同じ名前だと、ユーザーID検索と組み合わせた
 * 際にどちらの意味か曖昧になる)。
 */
@Controller
@RequestMapping("/admin/messages")
public class AdminMessagingController {
  private static final int PREVIEW_SAMPLE_SIZE = 10;

  private final AdminMessagingService service;
  private final UserRepository users;

  public AdminMessagingController(AdminMessagingService service, UserRepository users) {
    this.service = service;
    this.users = users;
  }

  @GetMapping
  String history(@RequestParam(defaultValue = "0") int page, Model model) {
    model.addAttribute("result", service.history(page));
    return "admin/messages/list";
  }

  @GetMapping("/{id}")
  String detail(@PathVariable Long id, Model model) {
    model.addAttribute("message", service.messageDetail(id));
    model.addAttribute("recipients", service.recipientsOf(id));
    return "admin/messages/detail";
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
    if (!model.containsAttribute("adminMessageForm")) model.addAttribute("adminMessageForm", new AdminMessageForm());
    populateTargetModel(model, selectedUserId, criteria);
    return "admin/messages/form";
  }

  /**
   * 対象は送信時点で固定: ここでtargetIdsを一度だけ確定させ(EXPLICITならその場のリスト、
   * CRITERIAならAdminUserSearchServiceを今この瞬間に評価した結果)、そのままRecipientの
   * 作成に使う。送信後にユーザーの属性(プラン・投稿数等)が変わっても、検索条件を
   * 保存して後から再評価する仕組みは無いため、既に確定した受信対象は変化しない。
   */
  @PostMapping
  String send(
      Principal principal,
      @Valid @ModelAttribute("adminMessageForm") AdminMessageForm form,
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
      if (targetIds.isEmpty()) model.addAttribute("targetError", "送信先が0人のため送信できません");
      populateTargetModelFrom(model, targetMode, targetIds, criteria);
      return "admin/messages/form";
    }

    var message = service.send(form.getTitle(), form.getBody(), form.getLinkUrl(), principal.getName(), targetIds);
    redirect.addFlashAttribute("success", targetIds.size() + "人に配信しました");
    return "redirect:/admin/messages/" + message.getId();
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
