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

  /**
   * wisdomUnlockedは呼び出し側(Controller)がcanReadExperiences(email)で判定した結果を渡す。
   * 検索結果は必ずPUBLISHED投稿のみを含むため(publicSearch参照)、「自分自身の公開投稿が
   * 1件以上ある」ことと「この一覧中の自分の投稿を持っている」ことは常に同値になり、
   * カード単位でcanReadWisdom(post,email)を呼び直す必要はない。
   */
  @Transactional(readOnly = true)
  public Page<ExperienceCardDto> search(
      ExperienceSearchCriteria criteria, int page, String sort, boolean wisdomUnlocked) {
    var order =
        "helpful".equals(sort)
            ? Sort.by(Sort.Order.desc("createdAt"))
            : Sort.by(Sort.Order.desc("createdAt"));
    return posts
        .findAll(
            ExperiencePostSpecifications.publicSearch(criteria),
            PageRequest.of(Math.max(0, page), 12, order))
        .map(p -> ExperienceCardDto.from(p, wisdomUnlocked));
  }

  @Transactional(readOnly = true)
  public List<ExperienceCardDto> latest(boolean wisdomUnlocked) {
    return posts.findTop6ByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED).stream()
        .map(p -> ExperienceCardDto.from(p, wisdomUnlocked))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ExperienceCardDto> recommended(boolean wisdomUnlocked) {
    return posts.findTop6ByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED).stream()
        .map(p -> ExperienceCardDto.from(p, wisdomUnlocked))
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
  public List<ExperienceCardDto> similar(ExperiencePost source, boolean wisdomUnlocked) {
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
    return search(c, 0, "latest", wisdomUnlocked).stream()
        .filter(p -> !p.id().equals(source.getId()))
        .limit(4)
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<ExperienceCardDto> byAuthor(Long userId, int page, boolean wisdomUnlocked) {
    return posts
        .findByAuthorIdAndStatus(
            userId,
            PostStatus.PUBLISHED,
            PageRequest.of(Math.max(page, 0), 12, Sort.by(Sort.Direction.DESC, "createdAt")))
        .map(p -> ExperienceCardDto.from(p, wisdomUnlocked));
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

  /** 通報対応の「非公開対応」専用。管理者のみ実行できる(投稿者本人には開放しない)。 */
  @Transactional
  public void hideByModeration(Long id, String adminEmail) {
    var admin = user(adminEmail);
    if (admin.getRole() != Role.ADMIN) throw new ForbiddenOperationException("この操作には管理者権限が必要です");
    find(id).hideByModeration();
  }

  @Transactional(readOnly = true)
  public boolean canManage(ExperiencePost post, String email) {
    return mayManage(post, email);
  }

  /**
   * 体験談の「学び」部分(振り返り)を閲覧できるかどうか。投稿者本人・管理者は常に自分の
   * 投稿の学びを閲覧できる。それ以外は、既存のgive to get判定(canReadExperiences)を
   * そのまま再利用し、自分自身の公開体験談を1件以上投稿しているユーザーにのみ許可する。
   */
  @Transactional(readOnly = true)
  public boolean canReadWisdom(ExperiencePost post, String email) {
    return mayManage(post, email) || canReadExperiences(email);
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
