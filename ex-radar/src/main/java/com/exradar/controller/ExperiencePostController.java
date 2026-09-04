package com.exradar.controller;

import com.exradar.dto.ExperienceCardDto;
import com.exradar.dto.ExperienceListPageDto;
import com.exradar.dto.ExperienceSearchCriteria;
import com.exradar.dto.ExperienceWisdomView;
import com.exradar.entity.ExperiencePost;
import com.exradar.entity.PostStatus;
import com.exradar.form.*;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.PersonalValueRepository;
import com.exradar.service.*;
import java.security.Principal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/experiences")
public class ExperiencePostController {
  private final ExperiencePostService service;
  private final InteractionService interactions;
  private final CategoryRepository categories;
  private final PersonalValueRepository values;
  private final RewardService rewards;

  public ExperiencePostController(
      ExperiencePostService service,
      InteractionService interactions,
      CategoryRepository categories,
      PersonalValueRepository values,
      RewardService rewards) {
    this.service = service;
    this.interactions = interactions;
    this.categories = categories;
    this.values = values;
    this.rewards = rewards;
  }

  /**
   * 投稿保存成功後に特典対象投稿数を再評価し、新たに達成したマイルストームがあればここで
   * 通知メッセージへ追記する(RewardServiceが付与自体とアプリ内通知(Notification)は既に
   * 行っているため、ここではフラッシュメッセージでの即時フィードバックのみを担う。
   * ロジック自体はRewardServiceに閉じているため、Controllerは呼び出すだけ)。
   */
  private String withRewardNotice(String baseMessage, com.exradar.entity.User author) {
    var granted = rewards.evaluateAndGrant(author);
    if (granted.isEmpty()) return baseMessage;
    var sb = new StringBuilder(baseMessage);
    for (var benefit : granted) {
      sb.append(" ").append(benefit.getSourceDescription())
          .append("！「").append(benefit.getBenefitNameSnapshot()).append("」特典を獲得しました。");
    }
    return sb.toString();
  }

  @ModelAttribute
  void categories(Model model) {
    model.addAttribute("categories", categories.findByActiveTrueOrderByDisplayOrder());
    model.addAttribute("personalValues", values.findAllByOrderByDisplayOrder());
  }

  @GetMapping
  String list(
      @ModelAttribute ExperienceSearchCriteria criteria,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "latest") String sort,
      Principal principal,
      Model model) {
    var email = principal == null ? null : principal.getName();
    boolean wisdomUnlocked = service.canReadExperiences(email);
    model.addAttribute("result", service.search(criteria, page, sort, wisdomUnlocked, email));
    model.addAttribute("wisdomUnlocked", wisdomUnlocked);
    model.addAttribute("sort", sort);
    // カテゴリのみで絞り込んでいる場合は「カテゴリページ」として自己canonical+専用の
    // タイトル・説明文を設定する。それ以外(キーワード検索やその他条件つき)は
    // 重複コンテンツを避けるため一覧の正規URL(/experiences)へcanonicalを寄せる。
    if (criteria.categoryOnly()) {
      categories
          .findById(criteria.categoryId())
          .ifPresent(
              c -> {
                model.addAttribute("categoryPageName", c.getName());
                model.addAttribute(
                    "listCanonicalPath", "/experiences?categoryId=" + c.getId());
              });
    }
    return "experiences/list";
  }

  /**
   * 一覧の「もっと見る」用JSON API。URLは通常の一覧ページと同じ/experiencesのままで、
   * Acceptヘッダによるコンテンツネゴシエーションでこちらへ振り分ける(SecurityConfigの
   * "^/experiences$"(GET)permitAllをそのまま再利用でき、新たなURL・権限設定が不要)。
   * DB側の絞り込み・並び替え・ページングをそのまま使うため、投稿数が増えても
   * 全件取得にはならない(service.searchが常にPageで返す)。
   */
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  ExperienceListPageDto listJson(
      @ModelAttribute ExperienceSearchCriteria criteria,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "latest") String sort,
      Principal principal) {
    var email = principal == null ? null : principal.getName();
    boolean wisdomUnlocked = service.canReadExperiences(email);
    return ExperienceListPageDto.from(service.search(criteria, page, sort, wisdomUnlocked, email));
  }

  /**
   * 体験談本文を最後まで読んだ(スクロールで検知)ことをサーバー側へ記録する。
   * ログイン必須(SecurityConfigの.anyRequest().authenticated()により、GETのみpermitAllの
   * 他のexperiencesエンドポイントとは異なりこのPOSTは自動的に認証必須になる)。
   */
  @PostMapping("/{id}/read")
  @ResponseBody
  ResponseEntity<Void> markRead(@PathVariable Long id, Principal principal) {
    interactions.markRead(id, principal.getName());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/new")
  String createForm(Model model) {
    if (!model.containsAttribute("experiencePostForm"))
      model.addAttribute("experiencePostForm", new ExperiencePostForm());
    model.addAttribute("editing", false);
    model.addAttribute("isDraft", true);
    return "experiences/form";
  }

  @GetMapping("/{id}/view-options")
  String viewOptions(@PathVariable Long id, Principal principal, Model model) {
    var email = principal == null ? null : principal.getName();
    var post = service.getVisible(id, email);
    model.addAttribute("post", post);
    addWisdom(model, post, email);
    return "experiences/view-options";
  }

  @GetMapping("/{id}/summary")
  String summary(@PathVariable Long id, Principal principal, Model model) {
    var email = principal == null ? null : principal.getName();
    var post = service.getVisible(id, email);
    model.addAttribute("post", post);
    addWisdom(model, post, email);
    return "experiences/summary";
  }

  @PostMapping
  String create(
      @Validated(PublishValidation.class) @ModelAttribute ExperiencePostForm form,
      BindingResult result,
      Principal principal,
      Model model,
      RedirectAttributes redirect) {
    if (result.hasErrors()) {
      model.addAttribute("editing", false);
      model.addAttribute("isDraft", true);
      return "experiences/form";
    }
    var post = service.create(form, principal.getName());
    redirect.addFlashAttribute("successMessage", withRewardNotice("経験談を投稿しました", post.getAuthor()));
    return "redirect:/experiences/" + post.getId();
  }

  @PostMapping("/draft")
  String createDraft(
      @Validated(DraftValidation.class) @ModelAttribute ExperiencePostForm form,
      BindingResult result,
      Principal principal,
      Model model,
      RedirectAttributes redirect) {
    if (result.hasErrors()) {
      model.addAttribute("editing", false);
      model.addAttribute("isDraft", true);
      return "experiences/form";
    }
    var post = service.createDraft(form, principal.getName());
    redirect.addFlashAttribute("successMessage", "下書きを保存しました");
    return "redirect:/experiences/" + post.getId() + "/edit";
  }

  @PostMapping(value = "/draft/autosave", produces = "application/json")
  @ResponseBody
  ResponseEntity<DraftSaveResult> autosaveCreate(
      @Validated(DraftValidation.class) @ModelAttribute ExperiencePostForm form,
      BindingResult result,
      Principal principal) {
    if (result.hasErrors()) return ResponseEntity.unprocessableEntity().build();
    var post = service.createDraft(form, principal.getName());
    return ResponseEntity.ok(new DraftSaveResult(post.getId(), post.getStatus().name()));
  }

  @GetMapping("/{id}")
  String detail(@PathVariable Long id, Principal principal, Model model) {
    // 「体験談全体のgive to get」は廃止。公開済み(PUBLISHED)の体験談は、誰でも
    // 「経験・失敗」部分を全文閲覧できる(SEO流入のため匿名アクセスも許可)。
    // 下書き・非公開・管理者による非公開対応(HIDDEN=DRAFT)はgetVisible内部で
    // 引き続き本人・管理者以外には404として扱われるため、閲覧制限自体はサーバー側で維持される。
    // 一方、「学び」(教訓・判断基準・今ならどうするか等)は、自分自身の公開体験談を
    // 1件以上投稿しているユーザーにのみ引き続き開放する(addWisdom参照)。
    var email = principal == null ? null : principal.getName();
    var post = service.getVisible(id, email);
    model.addAttribute("post", post);
    boolean wisdomUnlocked = addWisdom(model, post, email);
    model.addAttribute("metaDescription", SeoText.excerpt(publicSummary(post), 120));
    model.addAttribute("canManage", service.canManage(post, email));
    model.addAttribute(
        "canReact",
        email != null && !post.getAuthor().getEmail().equalsIgnoreCase(email));
    model.addAttribute("similarPosts", service.similar(post, wisdomUnlocked));
    if (post.isPublished()) {
      model.addAttribute("reactionSummary", interactions.reactions(id, email));
      model.addAttribute("comments", interactions.comments(id));
    }
    if (!model.containsAttribute("commentForm"))
      model.addAttribute("commentForm", new CommentForm());
    if (!model.containsAttribute("reportForm")) model.addAttribute("reportForm", new ReportForm());
    return "experiences/detail";
  }

  @GetMapping("/unlock")
  String unlock(Principal principal, Model model) {
    model.addAttribute("loggedIn", principal != null);
    model.addAttribute(
        "alreadyUnlocked", principal != null && service.canReadExperiences(principal.getName()));
    return "experiences/unlock";
  }

  @GetMapping("/{id}/edit")
  String editForm(@PathVariable Long id, Principal principal, Model model) {
    var post = service.getManageable(id, principal.getName());
    var form = ExperiencePostForm.from(post);
    form.setTagNames(String.join(", ", post.getTags().stream().map(t -> t.getName()).toList()));
    model.addAttribute("experiencePostForm", form);
    model.addAttribute("postId", id);
    model.addAttribute("editing", true);
    model.addAttribute("isDraft", post.getStatus() == PostStatus.DRAFT);
    return "experiences/form";
  }

  @PostMapping("/{id}")
  String update(
      @PathVariable Long id,
      @Validated(PublishValidation.class) @ModelAttribute ExperiencePostForm form,
      BindingResult result,
      Principal principal,
      Model model,
      RedirectAttributes redirect) {
    if (result.hasErrors()) {
      model.addAttribute("postId", id);
      model.addAttribute("editing", true);
      model.addAttribute("isDraft", false);
      return "experiences/form";
    }
    var post = service.update(id, form, principal.getName());
    redirect.addFlashAttribute("successMessage", withRewardNotice("体験談を更新しました", post.getAuthor()));
    return "redirect:/experiences/" + id;
  }

  @PostMapping("/{id}/draft")
  String updateDraft(
      @PathVariable Long id,
      @Validated(DraftValidation.class) @ModelAttribute ExperiencePostForm form,
      BindingResult result,
      Principal principal,
      Model model,
      RedirectAttributes redirect) {
    if (result.hasErrors()) {
      model.addAttribute("postId", id);
      model.addAttribute("editing", true);
      model.addAttribute("isDraft", true);
      return "experiences/form";
    }
    service.updateDraft(id, form, principal.getName());
    redirect.addFlashAttribute("successMessage", "下書きを保存しました");
    return "redirect:/experiences/" + id + "/edit";
  }

  @PostMapping(value = "/{id}/draft/autosave", produces = "application/json")
  @ResponseBody
  ResponseEntity<DraftSaveResult> autosaveUpdate(
      @PathVariable Long id,
      @Validated(DraftValidation.class) @ModelAttribute ExperiencePostForm form,
      BindingResult result,
      Principal principal) {
    if (result.hasErrors()) return ResponseEntity.unprocessableEntity().build();
    var post = service.updateDraft(id, form, principal.getName());
    return ResponseEntity.ok(new DraftSaveResult(post.getId(), post.getStatus().name()));
  }

  @PostMapping("/{id}/delete")
  String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
    service.delete(id, principal.getName());
    redirect.addFlashAttribute("successMessage", "体験談を削除しました");
    return "redirect:/mypage";
  }

  /**
   * 「学び」部分をModelへ追加する。閲覧権限がない場合はwisdomUnlocked=falseのみ設定し、
   * wisdom属性自体をModelへ入れない(nullのまま)。学び本文をサーバー側で一切渡さない
   * ことで、テンプレート側の実装ミスによる漏えいを構造的に防ぐ。
   */
  private boolean addWisdom(Model model, ExperiencePost post, String email) {
    boolean unlocked = service.canReadWisdom(post, email);
    model.addAttribute("wisdomUnlocked", unlocked);
    model.addAttribute("wisdom", unlocked ? ExperienceWisdomView.from(post) : null);
    return unlocked;
  }

  /**
   * meta descriptionに使う本文は、匿名ユーザーにも実際に公開している「経験・失敗」部分
   * (結果・大変だったこと)のみから生成する。「学び」(教訓・今ならどうするか等)は
   * 未解放の読者には読めないため、検索結果と実際に読める内容が食い違わないよう含めない。
   */
  private String publicSummary(ExperiencePost post) {
    var parts = new StringBuilder();
    if (post.getOutcome() != null && !post.getOutcome().isBlank()) parts.append(post.getOutcome());
    if (post.getDifficulties() != null && !post.getDifficulties().isBlank()) {
      if (!parts.isEmpty()) parts.append(' ');
      parts.append(post.getDifficulties());
    }
    return !parts.isEmpty() ? parts.toString() : post.getSituationBefore();
  }

  record DraftSaveResult(Long id, String status) {}
}
