package com.exradar.controller;

import com.exradar.dto.ExperienceSearchCriteria;
import com.exradar.entity.Category;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.TagRepository;
import com.exradar.service.ExperiencePostService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 教訓まとめ(/choices)。カテゴリごとの大きなカードから遷移する一覧ではなく、検索ボックス・
 * タグ・カテゴリで絞り込みながら教訓を高速に読み進めるフィード形式のページ。
 * 教訓(learned/lesson)は投稿を1件以上公開しているユーザーにのみ公開される「学び」の一部
 * であるため、旧・カテゴリ別詳細ページ(/choices/{slug})が持っていたログイン+
 * canReadExperiencesのゲートを、フィードそのものが常時教訓を表示する新しいページ全体に
 * そのまま引き継ぐ(挙動を緩めない)。
 */
@Controller
@RequestMapping("/choices")
public class ChoiceGuideController {
  private final CategoryRepository categories;
  private final TagRepository tags;
  private final ExperiencePostService experienceService;

  public ChoiceGuideController(
      CategoryRepository categories, TagRepository tags, ExperiencePostService experienceService) {
    this.categories = categories;
    this.tags = tags;
    this.experienceService = experienceService;
  }

  @GetMapping
  public String index(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "simple") String view,
      @RequestParam(defaultValue = "0") int page,
      Principal principal,
      Model m) {
    if (principal == null || !experienceService.canReadExperiences(principal.getName()))
      return "redirect:/experiences/unlock";

    var allCategories = categories.findByActiveTrueOrderByDisplayOrder();
    Long categoryId = null;
    if (category != null && !category.isBlank()) {
      // 存在しないslugが指定された場合は、404にはせず「0件」の検索結果として扱う
      // (フィード=検索結果ページという性質上、古いブックマーク等からのアクセスでも
      // 一覧の空状態UIがそのまま案内として機能するため)。
      categoryId = allCategories.stream()
          .filter(c -> c.getSlug().equals(category))
          .map(Category::getId)
          .findFirst()
          .orElse(-1L);
    }

    var criteria =
        new ExperienceSearchCriteria(
            q, categoryId, tag, null, null, null, null, null, null, null, null, null, null, null, null);
    var result = experienceService.lessonSearch(criteria, page, "latest", true, principal.getName());

    m.addAttribute("categories", allCategories);
    m.addAttribute("selectedCategorySlug", category);
    m.addAttribute("tagQuery", tag);
    m.addAttribute("keyword", q);
    m.addAttribute("view", "detailed".equals(view) ? "detailed" : "simple");
    m.addAttribute("result", result);
    m.addAttribute("popularTags", tags.findTop30ByOrderByNameAsc());
    return "choices/list";
  }

  /** 旧・カテゴリ別詳細ページ(/choices/{slug})への既存リンク・ブックマークが切れないよう、新URLへ転送する。 */
  @GetMapping("/{slug}")
  public String legacyDetailRedirect(@PathVariable String slug) {
    return "redirect:/choices?category=" + slug;
  }
}
