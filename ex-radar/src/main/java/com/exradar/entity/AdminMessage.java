package com.exradar.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 運営から会員への一括/個別メッセージ本体。受信者・既読状態はAdminMessageRecipientで
 * 別テーブルに分離している(1メッセージに対して多数の受信者が対応するため)。
 * createdByAdminがNULLになるのは、送信した管理者アカウントが退会した場合のみ
 * (AccountService#deleteAccount参照)。メッセージ本体・他ユーザーの受信記録は
 * 管理者の退会だけでは削除しない。
 */
@Entity
@Table(name = "admin_messages")
public class AdminMessage extends BaseEntity {
  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 4000)
  private String body;

  @Column(name = "link_url", length = 500)
  private String linkUrl;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_admin_id")
  private User createdByAdmin;

  @Column(name = "sent_at", nullable = false)
  private LocalDateTime sentAt;

  protected AdminMessage() {}

  public AdminMessage(String title, String body, String linkUrl, User createdByAdmin, LocalDateTime sentAt) {
    this.title = title;
    this.body = body;
    this.linkUrl = linkUrl;
    this.createdByAdmin = createdByAdmin;
    this.sentAt = sentAt;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public String getLinkUrl() {
    return linkUrl;
  }

  public User getCreatedByAdmin() {
    return createdByAdmin;
  }

  public LocalDateTime getSentAt() {
    return sentAt;
  }

  /** 送信した管理者が退会した場合に、送信者情報だけを外す(メッセージ本体・受信記録は残す)。 */
  public void clearCreatedByAdmin() {
    createdByAdmin = null;
  }
}
