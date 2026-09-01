package com.exradar.service;

import com.exradar.dto.ReactionSummary;
import com.exradar.entity.*;
import com.exradar.exception.*;
import com.exradar.form.*;
import com.exradar.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteractionService {
  private final ReactionRepository reactions;
  private final CommentRepository comments;
  private final NotificationRepository notifications;
  private final ReportRepository reports;
  private final ExperiencePostRepository posts;
  private final UserRepository users;

  public InteractionService(
      ReactionRepository reactions,
      CommentRepository comments,
      NotificationRepository notifications,
      ReportRepository reports,
      ExperiencePostRepository posts,
      UserRepository users) {
    this.reactions = reactions;
    this.comments = comments;
    this.notifications = notifications;
    this.reports = reports;
    this.posts = posts;
    this.users = users;
  }

  @Transactional
  public boolean toggleReaction(Long postId, ReactionType type, String email) {
    if (type == null) throw new ResourceNotFoundException("リアクションが見つかりません");
    var user = user(email);
    var post = publicPostForUpdate(postId);
    if (post.getAuthor().getId().equals(user.getId()))
      throw new ForbiddenOperationException("自分の体験談にはリアクションできません");
    var old = reactions.findByUserIdAndPostIdAndType(user.getId(), postId, type);
    if (old.isPresent()) {
      reactions.delete(old.get());
      return false;
    }
    reactions.save(new Reaction(user, post, type));
    notify(post, user, NotificationType.REACTION, user.getDisplayName() + "さんがあなたの体験談にリアクションしました");
    return true;
  }

  @Transactional(readOnly = true)
  public ReactionSummary reactions(Long postId, String email) {
    publicPost(postId);
    var counts = new EnumMap<ReactionType, Long>(ReactionType.class);
    for (var type : ReactionType.values())
      counts.put(type, reactions.countByPostIdAndType(postId, type));
    var mine = EnumSet.noneOf(ReactionType.class);
    if (email != null) {
      var u = user(email);
      reactions.findByUserIdAndPostId(u.getId(), postId).forEach(r -> mine.add(r.getType()));
    }
    return new ReactionSummary(counts, mine);
  }

  @Transactional
  public Comment addComment(Long postId, CommentForm form, String email) {
    var post = publicPost(postId);
    var author = user(email);
    var comment = comments.save(new Comment(post, author, form.getBody().trim()));
    notify(post, author, NotificationType.COMMENT, author.getDisplayName() + "さんがあなたの体験談にコメントしました");
    return comment;
  }

  @Transactional(readOnly = true)
  public List<Comment> comments(Long postId) {
    publicPost(postId);
    return comments.findByPostIdOrderByCreatedAtAsc(postId);
  }

  @Transactional
  public void deleteComment(Long postId, Long id, String email) {
    if (postId == null || postId < 1 || id == null || id < 1)
      throw new ResourceNotFoundException("コメントが見つかりません");
    var c =
        comments
            .findById(id)
            .filter(v -> v.getPost().getId().equals(postId))
            .orElseThrow(() -> new ResourceNotFoundException("コメントが見つかりません"));
    var u = user(email);
    if (u.getRole() != Role.ADMIN && !c.getAuthor().getId().equals(u.getId()))
      throw new ForbiddenOperationException("このコメントを削除する権限がありません");
    comments.delete(c);
  }

  @Transactional
  public void report(ReportForm form, String email) {
    var reporter = user(email);
    verifyTarget(form.getTargetType(), form.getTargetId());
    reports.save(
        new Report(reporter, form.getTargetType(), form.getTargetId(), form.getReason().trim()));
  }

  private void verifyTarget(ReportTargetType type, Long id) {
    if (type == null || id == null || id < 1) throw new ResourceNotFoundException("通報対象が見つかりません");
    switch (type) {
      case EXPERIENCE_POST -> publicPost(id);
      case COMMENT -> {
        var c =
            comments.findById(id).orElseThrow(() -> new ResourceNotFoundException("通報対象が見つかりません"));
        if (!c.getPost().isPublished()) throw new ResourceNotFoundException("通報対象が見つかりません");
      }
    }
  }

  private ExperiencePost publicPost(Long id) {
    if (id == null || id < 1) throw new ResourceNotFoundException("体験談が見つかりません");
    return posts
        .findDetailedById(id)
        .filter(ExperiencePost::isPublished)
        .orElseThrow(() -> new ResourceNotFoundException("体験談が見つかりません"));
  }

  private ExperiencePost publicPostForUpdate(Long id) {
    if (id == null || id < 1) throw new ResourceNotFoundException("体験談が見つかりません");
    return posts
        .findForInteraction(id)
        .filter(ExperiencePost::isPublished)
        .orElseThrow(() -> new ResourceNotFoundException("体験談が見つかりません"));
  }

  private User user(String email) {
    if (email == null) throw new ForbiddenOperationException("ログインが必要です");
    return users
        .findByEmailIgnoreCase(email)
        .orElseThrow(() -> new ForbiddenOperationException("ユーザーを確認できません"));
  }

  private void notify(ExperiencePost post, User actor, NotificationType type, String message) {
    if (!post.getAuthor().getId().equals(actor.getId()))
      notifications.save(new Notification(post.getAuthor(), type, message, post.getId()));
  }
}
