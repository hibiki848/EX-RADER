package com.exradar.service;

import com.exradar.entity.*;
import com.exradar.exception.*;
import com.exradar.form.*;
import com.exradar.repository.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private final UserRepository users;
  private final ExperiencePostRepository posts;
  private final ReactionRepository reactions;
  private final NotificationRepository notifications;
  private final CommentRepository comments;
  private final DecisionMemoRepository memos;
  private final ReportRepository reports;
  private final ExperienceReadRepository reads;
  private final AdminMessageRecipientRepository adminMessageRecipients;
  private final AdminMessageRepository adminMessages;
  private final PasswordEncoder encoder;

  public AccountService(
      UserRepository u,
      ExperiencePostRepository p,
      ReactionRepository r,
      NotificationRepository n,
      CommentRepository c,
      DecisionMemoRepository m,
      ReportRepository reports,
      ExperienceReadRepository reads,
      AdminMessageRecipientRepository adminMessageRecipients,
      AdminMessageRepository adminMessages,
      PasswordEncoder e) {
    users = u;
    posts = p;
    reactions = r;
    notifications = n;
    comments = c;
    memos = m;
    this.reports = reports;
    this.reads = reads;
    this.adminMessageRecipients = adminMessageRecipients;
    this.adminMessages = adminMessages;
    encoder = e;
  }

  @Transactional(readOnly = true)
  public User current(String email) {
    var u =
        users
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResourceNotFoundException("ユーザーが見つかりません"));
    if (u.isSuspended()) throw new ForbiddenOperationException("停止中のアカウントです");
    return u;
  }

  @Transactional
  public void updateProfile(String email, ProfileForm f) {
    var u = current(email);
    u.updateProfile(
        clean(f.getDisplayName()),
        clean(f.getAgeGroup()),
        clean(f.getEducation()),
        clean(f.getOccupation()),
        clean(f.getPrefecture()),
        clean(f.getBiography()));
  }

  @Transactional
  public void changePassword(String email, PasswordChangeForm f) {
    var u = current(email);
    if (!encoder.matches(f.getCurrentPassword(), u.getPassword()))
      throw new IllegalArgumentException("現在のパスワードが正しくありません");
    if (!f.getNewPassword().equals(f.getConfirmation()))
      throw new IllegalArgumentException("確認用パスワードが一致しません");
    if (encoder.matches(f.getNewPassword(), u.getPassword()))
      throw new IllegalArgumentException("現在と異なるパスワードを設定してください");
    u.changePassword(encoder.encode(f.getNewPassword()));
  }

  @Transactional(readOnly = true)
  public List<ExperiencePost> posts(String email) {
    return posts.findByAuthorIdAndStatus(current(email).getId(), PostStatus.PUBLISHED);
  }

  @Transactional(readOnly = true)
  public List<ExperiencePost> drafts(String email) {
    return posts.findByAuthorIdAndStatusOrderByUpdatedAtDesc(current(email).getId(), PostStatus.DRAFT);
  }

  @Transactional(readOnly = true)
  public List<Reaction> reactions(String email) {
    return reactions.findByUserIdOrderByCreatedAtDesc(current(email).getId());
  }

  @Transactional(readOnly = true)
  public List<Notification> notifications(String email) {
    return notifications.findByRecipientIdOrderByCreatedAtDesc(current(email).getId());
  }

  @Transactional(readOnly = true)
  public long unread(String email) {
    return notifications.countByRecipientIdAndReadFlagFalse(current(email).getId());
  }

  @Transactional
  public void read(String email, Long id) {
    var u = current(email);
    var n =
        notifications.findById(id).orElseThrow(() -> new ResourceNotFoundException("通知が見つかりません"));
    if (!n.getRecipient().getId().equals(u.getId()))
      throw new ForbiddenOperationException("他のユーザーの通知は操作できません");
    n.markRead();
  }

  @Transactional
  public void readAll(String email) {
    for (var n : notifications.findByRecipientIdOrderByCreatedAtDesc(current(email).getId()))
      n.markRead();
  }

  @Transactional
  public void deleteAccount(String email, String password) {
    var user = current(email);
    if (!encoder.matches(password, user.getPassword()))
      throw new IllegalArgumentException("現在のパスワードが正しくありません");

    var userId = user.getId();
    var ownedPosts = posts.findByAuthorId(userId);
    comments.deleteByAuthorId(userId);
    reactions.deleteByUserId(userId);
    reads.deleteByUserId(userId);
    notifications.deleteByRecipientId(userId);
    memos.deleteByUserId(userId);
    reports.deleteByReporterId(userId);
    // 退会するユーザー自身の受信記録は削除する。送信した管理者メッセージ本体・他の
    // 受信者の記録は、1人の退会だけでは消えないよう送信者情報だけ外す(FK制約回避)。
    adminMessageRecipients.deleteByUserId(userId);
    adminMessages.clearCreatedByAdmin(userId);
    for (var post : ownedPosts) {
      comments.deleteByPostId(post.getId());
      reactions.deleteByPostId(post.getId());
      reads.deleteByPostId(post.getId());
      posts.delete(post);
    }
    users.delete(user);
  }

  private String clean(String v) {
    return v == null ? null : v.trim();
  }
}
