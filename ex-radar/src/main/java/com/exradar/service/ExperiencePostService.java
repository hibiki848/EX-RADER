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
    return posts.findTop6ByPublishedTrueOrderByCreatedAtDesc().stream()
        .map(ExperienceCardDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ExperienceCardDto> recommended() {
    return posts.findTop6ByPublishedTrueOrderByCreatedAtDesc().stream()
        .map(ExperienceCardDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public boolean canReadExperiences(String email) {
    if (email == null || email.isBlank()) return false;
    return users
        .findByEmailIgnoreCase(email)
        .filter(u -> !u.isSuspended())
      .map(u -> u.getRole() == Role.ADMIN || posts.existsByAuthorIdAndPublishedTrue(u.getId()))
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
        .findByAuthorIdAndPublishedTrue(
            userId,
            PageRequest.of(Math.max(page, 0), 12, Sort.by(Sort.Direction.DESC, "createdAt")))
        .map(ExperienceCardDto::from);
  }

  @Transactional
  public ExperiencePost create(ExperiencePostForm form, String email) {
    var user = user(email);
    var post = new ExperiencePost(user);
    apply(post, form);
    return posts.save(post);
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

  @Transactional
  public ExperiencePost update(Long id, ExperiencePostForm form, String email) {
    var post = find(id);
    requireManage(post, email);
    apply(post, form);
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

  private void apply(ExperiencePost p, ExperiencePostForm f) {
    var category =
        categories
            .findById(f.getCategoryId())
            .filter(Category::isActive)
            .orElseThrow(() -> new ResourceNotFoundException("カテゴリが見つかりません"));
    p.update(
        category,
        f.getTitle(),
        f.getAgeAtChoice(),
        f.getStatusAtChoice(),
        f.getCurrentAgeGroup(),
        f.getYearsElapsed(),
        f.getSituationBefore(),
        f.getWorries(),
        f.getAlternatives(),
        f.getChoiceMade(),
        f.getReason(),
        f.getOutcome(),
        f.getGoodThings(),
        f.getDifficulties(),
        f.getUnexpectedThings(),
        f.getSatisfaction(),
        f.getRegret(),
        f.isChooseAgain(),
        f.getAdviceToPastSelf(),
        f.isPublished());
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
