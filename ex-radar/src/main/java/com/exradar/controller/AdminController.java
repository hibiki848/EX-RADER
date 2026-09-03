package com.exradar.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.time.Duration;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.exradar.dto.AdminUserSearchCriteria;
import com.exradar.dto.AdminUserSortField;
import com.exradar.entity.PlanType;
import com.exradar.entity.Role;
import com.exradar.service.AdminService;
import com.exradar.service.AdminUserSearchService;

@Controller
@RequestMapping("/admin")
public class AdminController {
  private static final Duration BROWSER_EXCLUSION_COOKIE_MAX_AGE = Duration.ofDays(400);

  private final AdminService service;
  private final AdminUserSearchService userSearch;

  public AdminController(AdminService service, AdminUserSearchService userSearch) {
    this.service = service;
    this.userSearch = userSearch;
  }

  @ModelAttribute
  void searchFormOptions(Model model) {
    model.addAttribute("planOptions", PlanType.values());
    model.addAttribute("sortFieldOptions", AdminUserSortField.values());
  }

  @GetMapping
  public String dashboard(
      Principal principal,
      @ModelAttribute AdminUserSearchCriteria criteria,
      @RequestParam(required = false) String filter,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "REGISTERED_AT") AdminUserSortField sort,
      @RequestParam(defaultValue = "DESC") Sort.Direction direction,
      HttpServletRequest request,
      Model model) {
    var effectiveCriteria = withLegacyFilter(criteria, filter);
    model.addAttribute("userCount", service.userCount());
    model.addAttribute("adminCount", service.adminCount());
    model.addAttribute("suspendedUserCount", service.suspendedUserCount());
    model.addAttribute("publishedPostCount", service.publishedPostCount());
    model.addAttribute("result", userSearch.search(effectiveCriteria, page, sort, direction));
    model.addAttribute("criteria", criteria);
    model.addAttribute("sort", sort);
    model.addAttribute("direction", direction);
    model.addAttribute("filter", filter == null ? "all" : filter);
    model.addAttribute("currentEmail", principal.getName());
    model.addAttribute("browserAnalyticsExcluded", NavigationAdvice.isBrowserExcluded(request));
    return "admin/dashboard";
  }

  @GetMapping("/users/{id}")
  public String userPosts(
      Principal principal, @PathVariable Long id, HttpServletRequest request, Model model) {
    model.addAttribute("userCount", service.userCount());
    model.addAttribute("adminCount", service.adminCount());
    model.addAttribute("suspendedUserCount", service.suspendedUserCount());
    model.addAttribute("publishedPostCount", service.publishedPostCount());
    model.addAttribute(
        "result",
        userSearch.search(
            AdminUserSearchCriteria.empty(), 0, AdminUserSortField.REGISTERED_AT, Sort.Direction.DESC));
    model.addAttribute("criteria", AdminUserSearchCriteria.empty());
    model.addAttribute("sort", AdminUserSortField.REGISTERED_AT);
    model.addAttribute("direction", Sort.Direction.DESC);
    model.addAttribute("filter", "all");
    model.addAttribute("currentEmail", principal.getName());
    model.addAttribute("selectedUser", service.user(id));
    model.addAttribute("selectedUserPosts", service.posts(id));
    model.addAttribute("browserAnalyticsExcluded", NavigationAdvice.isBrowserExcluded(request));
    return "admin/dashboard";
  }

  /**
   * 従来の「すべて/管理者のみ/停止中のみ」クイックフィルタ(filter=all/admins/suspended)は、
   * AdminUserSearchCriteriaのrole/suspendedへ変換して既存のリンク・挙動をそのまま維持する。
   * 検索フォーム側でrole/suspendedを直接指定した場合はそちらを優先する。
   */
  private AdminUserSearchCriteria withLegacyFilter(AdminUserSearchCriteria criteria, String filter) {
    if (criteria.role() != null || criteria.suspended() != null || filter == null) return criteria;
    if ("admins".equals(filter)) return withRole(criteria, Role.ADMIN);
    if ("suspended".equals(filter)) return withSuspended(criteria, true);
    return criteria;
  }

  private AdminUserSearchCriteria withRole(AdminUserSearchCriteria c, Role role) {
    return new AdminUserSearchCriteria(
        c.name(), c.email(), c.userId(), role, c.suspended(), c.plans(), c.registeredFrom(),
        c.registeredTo(), c.registeredDaysAgoMin(), c.registeredDaysAgoMax(), c.firstLoginFrom(),
        c.firstLoginTo(), c.lastLoginFrom(), c.lastLoginTo(), c.neverLoggedIn(), c.firstPostFrom(),
        c.firstPostTo(), c.hasPosted(), c.postCountMin(), c.postCountMax(), c.everPaid(),
        c.firstPaidFrom(), c.firstPaidTo(), c.currentlyPaid(), c.paidDurationMinDays(),
        c.paidDurationMaxDays());
  }

  private AdminUserSearchCriteria withSuspended(AdminUserSearchCriteria c, boolean suspended) {
    return new AdminUserSearchCriteria(
        c.name(), c.email(), c.userId(), c.role(), suspended, c.plans(), c.registeredFrom(),
        c.registeredTo(), c.registeredDaysAgoMin(), c.registeredDaysAgoMax(), c.firstLoginFrom(),
        c.firstLoginTo(), c.lastLoginFrom(), c.lastLoginTo(), c.neverLoggedIn(), c.firstPostFrom(),
        c.firstPostTo(), c.hasPosted(), c.postCountMin(), c.postCountMax(), c.everPaid(),
        c.firstPaidFrom(), c.firstPaidTo(), c.currentlyPaid(), c.paidDurationMinDays(),
        c.paidDurationMaxDays());
  }

  @PostMapping("/browser-analytics-exclusion")
  public String setBrowserAnalyticsExclusion(
      @RequestParam boolean excluded, HttpServletResponse response, RedirectAttributes redirect) {
    ResponseCookie cookie =
        ResponseCookie.from(NavigationAdvice.BROWSER_EXCLUSION_COOKIE, excluded ? "1" : "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(excluded ? BROWSER_EXCLUSION_COOKIE_MAX_AGE : Duration.ZERO)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    redirect.addFlashAttribute(
        "success",
        excluded ? "このブラウザをアクセス解析から除外しました" : "このブラウザをアクセス解析の対象に戻しました");
    return "redirect:/admin";
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

  @PostMapping("/users/{id}/analytics-exclusion")
  public String changeAnalyticsExclusion(
      @PathVariable Long id, @RequestParam boolean excluded, RedirectAttributes redirect) {
    return perform(
        redirect,
        () -> service.setAnalyticsExcluded(id, excluded),
        excluded ? "アクセス解析の対象から除外しました" : "アクセス解析の対象に戻しました");
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
