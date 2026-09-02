package com.exradar.service;

import static org.assertj.core.api.Assertions.*;

import com.exradar.entity.*;
import com.exradar.form.*;
import com.exradar.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InteractionServiceTest {
  @Autowired InteractionService service;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired ExperiencePostRepository posts;
  @Autowired ReactionRepository reactions;
  @Autowired NotificationRepository notifications;
  @Autowired CommentRepository comments;
  @Autowired ReportRepository reports;
  @Autowired PasswordEncoder encoder;
  User owner, other, admin;
  ExperiencePost post;

  @BeforeEach
  void setup() {
    owner =
        users.save(
            new User(
                "interaction-owner@example.com", encoder.encode("password"), "投稿者", Role.USER));
    other =
        users.save(
            new User("interaction-other@example.com", encoder.encode("password"), "読者", Role.USER));
    admin =
        users.save(
            new User(
                "interaction-admin@example.com", encoder.encode("password"), "管理者", Role.ADMIN));
    var category = categories.save(new Category("交流テスト", "interaction-test", 99));
    post = new ExperiencePost(owner);
    post.updateContent(
        category, "体験談", 25, "会社員", "30代", 5, "状況", "悩み", "選択肢", "選択", "理由", "結果", "良かった", "大変",
        "想定外", 8, 2, true, "助言");
    post.publish();
    post = posts.save(post);
  }

  @Test
  void reactionTogglesAndNotifiesOnlyOnAdd() {
    assertThat(service.toggleReaction(post.getId(), ReactionType.HELPFUL, other.getEmail()))
        .isTrue();
    assertThat(service.reactions(post.getId(), other.getEmail()).count(ReactionType.HELPFUL))
        .isEqualTo(1);
    assertThat(notifications.count()).isEqualTo(1);
    assertThat(service.toggleReaction(post.getId(), ReactionType.HELPFUL, other.getEmail()))
        .isFalse();
    assertThat(reactions.count()).isZero();
    assertThat(notifications.count()).isEqualTo(1);
  }

  @Test
  void ownerCannotReactToOwnPost() {
    assertThatThrownBy(
            () -> service.toggleReaction(post.getId(), ReactionType.SIMILAR, owner.getEmail()))
        .isInstanceOf(com.exradar.exception.ForbiddenOperationException.class);
    assertThat(reactions.count()).isZero();
    assertThat(notifications.count()).isZero();
  }

  @Test
  void selfCommentDoesNotNotify() {
    var form = new CommentForm();
    form.setBody(" 自分の補足 ");
    service.addComment(post.getId(), form, owner.getEmail());
    assertThat(notifications.count()).isZero();
  }

  @Test
  void ownerAndAdminCanDeleteCommentButOtherCannot() {
    var form = new CommentForm();
    form.setBody("コメント");
    var comment = service.addComment(post.getId(), form, other.getEmail());
    assertThatThrownBy(() -> service.deleteComment(post.getId(), comment.getId(), owner.getEmail()))
        .isInstanceOf(com.exradar.exception.ForbiddenOperationException.class);
    service.deleteComment(post.getId(), comment.getId(), admin.getEmail());
    assertThat(comments.findById(comment.getId())).isEmpty();
  }

  @Test
  void createsReportForExistingPublicTarget() {
    var form = new ReportForm();
    form.setTargetType(ReportTargetType.EXPERIENCE_POST);
    form.setTargetId(post.getId());
    form.setReason("不適切な内容が含まれています");
    service.report(form, other.getEmail());
    assertThat(reports.count()).isEqualTo(1);
  }

  @Test
  void databaseRejectsDuplicateReaction() {
    reactions.saveAndFlush(new Reaction(other, post, ReactionType.HELPFUL));
    assertThatThrownBy(
            () -> reactions.saveAndFlush(new Reaction(other, post, ReactionType.HELPFUL)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
}
