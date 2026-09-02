package com.exradar.service;

import com.exradar.dto.*;
import com.exradar.entity.*;
import com.exradar.exception.*;
import com.exradar.form.*;
import com.exradar.repository.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperiencePostService {
  private final ExperiencePostRepository posts;
  private final UserRepository users;
  private final CategoryRepository categories;
  private final TagRepository tags;
  private final PersonalValueRepository values;

  public ExperiencePostService(
      ExperiencePostRepository posts,
      UserRepository users,
      CategoryRepository categories,
      TagRepository tags,
      PersonalValueRepository values) {
    this.posts = posts;
    this.users = users;
    this.categories = categories;
    this.tags = tags;
    this.values = values;
  }

  @Transactional(readOnly = true)
  public Page<ExperienceCardDto> search(ExperienceSearchCriteria criteria, int page, String sort) {
    var order =
        "helpful".equals(sort)
            ? Sort.by(Sort.Order.desc("createdAt"))
            : Sort.by(Sort.Order.desc("createdAt"));
    return posts
        .findAll(
            ExperiencePostSpecifications.publicSearch(criteria),
            PageRequest.of(Math.max(0, page), 12, order))
        .map(ExperienceCardDto::from);
  }

  @Transactional(readOnly = true)
  public List<ExperienceCardDto> latest() {
    return posts.findTop6ByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED).stream()
        .map(ExperienceCardDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ExperienceCardDto> recommended() {
    return posts.findTop6ByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED).stream()
        .map(ExperienceCardDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public boolean canReadExperiences(String email) {
    if (email == null || email.isBlank()) return false;
    return users
        .findByEmailIgnoreCase(email)
        .filter(u -> !u.isSuspended())
        .map(
            u ->
                u.getRole() == Role.ADMIN
                    || posts.existsByAuthorIdAndStatus(u.getId(), PostStatus.PUBLISHED))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public List<ExperienceCardDto> similar(ExperiencePost source) {
    var c =
        new ExperienceSearchCriteria(
            null,
            source.getCategory().getId(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    return search(c, 0, "latest").stream()
        .filter(p -> !p.id().equals(source.getId()))
        .limit(4)
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<ExperienceCardDto> byAuthor(Long userId, int page) {
    return posts
        .findByAuthorIdAndStatus(
            userId,
            PostStatus.PUBLISHED,
            PageRequest.of(Math.max(page, 0), 12, Sort.by(Sort.Direction.DESC, "createdAt")))
        .map(ExperienceCardDto::from);
  }

  @Transactional
  public ExperiencePost create(ExperiencePostForm form, String email) {
    var user = user(email);
    var post = new ExperiencePost(user);
    applyContent(post, form);
    post.publish();
    return posts.save(post);
  }

  /** 新規の下書きを作成する。手動の「下書き保存」ボタンと自動保存の初回呼び出しの両方から使う。 */
  @Transactional
  public ExperiencePost createDraft(ExperiencePostForm form, String email) {
    var user = user(email);
    var post = new ExperiencePost(user);
    applyContent(post, form);
    return posts.save(post);
  }

  /**
   * 既存の下書きの本文を更新する。statusには一切触れないため、公開済み投稿に対して
   * 誤って呼び出された場合は例外を投げて何も変更しない(公開済み投稿の内容が
   * 緩いバリデーションで上書きされることも、DRAFTへ戻ることも構造的に起こらない)。
   */
  @Transactional
  public ExperiencePost updateDraft(Long id, ExperiencePostForm form, String email) {
    var post = find(id);
    requireManage(post, email);
    if (post.getStatus() != PostStatus.DRAFT)
      throw new ForbiddenOperationException("公開済みの投稿は下書き保存できません");
    applyContent(post, form);
    return post;
  }

  @Transactional(readOnly = true)
  public ExperiencePost getVisible(Long id, String email) {
    var post = find(id);
    if (!post.isPublished() && !mayManage(post, email))
      throw new ResourceNotFoundException("体験談が見つかりません");
    return post;
  }

  @Transactional(readOnly = true)
  public ExperiencePost getManageable(Long id, String email) {
    var post = find(id);
    requireManage(post, email);
    return post;
  }

  /** 「投稿する」「変更を保存」の両方から使う厳格な保存。DRAFT→PUBLISHEDへの遷移もこれで行う。 */
  @Transactional
  public ExperiencePost update(Long id, ExperiencePostForm form, String email) {
    var post = find(id);
    requireManage(post, email);
    applyContent(post, form);
    post.publish();
    return post;
  }

  @Transactional
  public void delete(Long id, String email) {
    var post = find(id);
    requireManage(post, email);
    posts.delete(post);
  }

  @Transactional(readOnly = true)
  public boolean canManage(ExperiencePost post, String email) {
    return mayManage(post, email);
  }

  private ExperiencePost find(Long id) {
    if (id == null || id < 1) throw new ResourceNotFoundException("体験談が見つかりません");
    return posts
        .findDetailedById(id)
        .orElseThrow(() -> new ResourceNotFoundException("体験談が見つかりません"));
  }

  private User user(String email) {
    if (email == null) throw new ForbiddenOperationException("ログインが必要です");
    return users
        .findByEmailIgnoreCase(email)
        .orElseThrow(() -> new ForbiddenOperationException("ユーザーを確認できません"));
  }

  private boolean mayManage(ExperiencePost p, String email) {
    if (email == null) return false;
    var u = user(email);
    return u.getRole() == Role.ADMIN || p.getAuthor().getId().equals(u.getId());
  }

  private void requireManage(ExperiencePost p, String email) {
    if (!mayManage(p, email)) throw new ForbiddenOperationException("この体験談を編集・削除する権限がありません");
  }

  private String nullToEmpty(String v) {
    return v == null ? "" : v;
  }

  private void applyContent(ExperiencePost p, ExperiencePostForm f) {
    // 下書きはカテゴリ未選択のまま保存されうるため、未選択(null)はそのまま許容する。
    // 選択されている場合は既存どおり実在・有効なカテゴリであることを検証する。
    Category category = null;
    if (f.getCategoryId() != null) {
      category =
          categories
              .findById(f.getCategoryId())
              .filter(Category::isActive)
              .orElseThrow(() -> new ResourceNotFoundException("カテゴリが見つかりません"));
    }
    // これらはDB上NOT NULLのため、下書きで未入力(Java側でnull)でも空文字として保存する。
    // 実際のフォーム送信では空のtextarea/inputは常に""として届くが、
    // プログラムから直接Formを組み立てる呼び出し元(テスト等)にも安全なようにここで正規化する。
    p.updateContent(
        category,
        nullToEmpty(f.getTitle()),
        f.getAgeAtChoice(),
        f.getStatusAtChoice(),
        f.getCurrentAgeGroup(),
        f.getYearsElapsed(),
        nullToEmpty(f.getSituationBefore()),
        nullToEmpty(f.getWorries()),
        nullToEmpty(f.getAlternatives()),
        nullToEmpty(f.getChoiceMade()),
        nullToEmpty(f.getReason()),
        nullToEmpty(f.getOutcome()),
        nullToEmpty(f.getGoodThings()),
        nullToEmpty(f.getDifficulties()),
        nullToEmpty(f.getUnexpectedThings()),
        f.getSatisfaction(),
        f.getRegret(),
        f.isChooseAgain(),
        nullToEmpty(f.getAdviceToPastSelf()));
    var events = new ArrayList<LifeEvent>();
    int order = 0;
    for (var e : f.getLifeEvents()) {
      if (e.getTitle() != null && !e.getTitle().isBlank())
        events.add(new LifeEvent(e.getAgeLabel(), e.getTitle(), e.getDescription(), order++));
    }
    p.replaceLifeEvents(events);
    var values = new LinkedHashSet<Tag>();
    if (f.getTagNames() != null)
      for (String raw : f.getTagNames().split("[,、]")) {
        String name = raw.trim().replaceFirst("^#", "");
        if (!name.isBlank() && name.length() <= 50 && values.size() < 10)
          values.add(tags.findByNameIgnoreCase(name).orElseGet(() -> tags.save(new Tag(name))));
      }
    p.replaceTags(values);
    var selectedValues =
        f.getValueIds() == null
            ? List.<PersonalValue>of()
            : this.values.findAllById(f.getValueIds());
    p.updateWisdom(
        f.getDecisionCriteria(),
        f.getLearned(),
        f.getWishKnown(),
        f.getUnexpectedlyOkay(),
        f.getPreparationHelped(),
        f.getMissedRegret(),
        f.getLesson(),
        f.getSuitableFor(),
        f.getCautionFor(),
        selectedValues);
  }
}
