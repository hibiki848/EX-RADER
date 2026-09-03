package com.exradar.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * AdminMessageの受信者1人分の配信・既読状態。(message, user)にUNIQUE制約があり、
 * 同一メッセージが同じユーザーへ重複登録されることはない。
 * readAtは単なるboolean既読フラグではなく実際の既読日時を持たせている
 * (未読率・開封率・配信後何時間で読まれたか、を後から分析できるようにするため)。
 */
@Entity
@Table(
    name = "admin_message_recipients",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_admin_message_recipient", columnNames = {"message_id", "user_id"}))
public class AdminMessageRecipient extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "message_id")
  private AdminMessage message;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "read_at")
  private LocalDateTime readAt;

  protected AdminMessageRecipient() {}

  public AdminMessageRecipient(AdminMessage message, User user) {
    this.message = message;
    this.user = user;
  }

  public AdminMessage getMessage() {
    return message;
  }

  public User getUser() {
    return user;
  }

  public LocalDateTime getReadAt() {
    return readAt;
  }

  public boolean isRead() {
    return readAt != null;
  }

  /** 同じ通知を何度開いても初回の既読日時が保たれるよう、既にreadAtがあれば何もしない(冪等)。 */
  public void markRead(LocalDateTime at) {
    if (readAt == null) readAt = at;
  }
}
