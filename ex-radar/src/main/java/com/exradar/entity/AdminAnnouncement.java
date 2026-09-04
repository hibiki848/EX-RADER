package com.exradar.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * ログイン後モーダルで表示する「運営からのお知らせ」本体。既存の運営メッセージ
 * (AdminMessage、通知一覧に残るBOX型)とは役割を分ける独立機能: こちらは
 * ログイン後ページ表示時に一度だけ画面中央モーダルとして見せる想定のため、
 * 公開期間(startsAt/endsAt)・公開状態(published)・表示優先度(priority)を持つ。
 * 対象者ごとの表示状態はAdminAnnouncementRecipientで別テーブルに分離している。
 * createdByAdminがNULLになるのは、作成した管理者アカウントが退会した場合のみ
 * (AccountService#deleteAccount参照)。お知らせ本体・他ユーザーの表示記録は
 * 管理者の退会だけでは削除しない。
 */
@Entity
@Table(name = "admin_announcements")
public class AdminAnnouncement extends BaseEntity {
  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 4000)
  private String body;

  @Column(name = "link_url", length = 500)
  private String linkUrl;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_admin_id")
  private User createdByAdmin;

  @Column(name = "starts_at", nullable = false)
  private LocalDateTime startsAt;

  @Column(name = "ends_at")
  private LocalDateTime endsAt;

  @Column(nullable = false)
  private boolean published;

  @Column(nullable = false)
  private int priority;

  protected AdminAnnouncement() {}

  public AdminAnnouncement(
      String title,
      String body,
      String linkUrl,
      User createdByAdmin,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      int priority) {
    this.title = title;
    this.body = body;
    this.linkUrl = linkUrl;
    this.createdByAdmin = createdByAdmin;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.priority = priority;
    this.published = false;
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

  public LocalDateTime getStartsAt() {
    return startsAt;
  }

  public LocalDateTime getEndsAt() {
    return endsAt;
  }

  public boolean isPublished() {
    return published;
  }

  public int getPriority() {
    return priority;
  }

  public void updateContent(
      String title, String body, String linkUrl, LocalDateTime startsAt, LocalDateTime endsAt, int priority) {
    this.title = title;
    this.body = body;
    this.linkUrl = linkUrl;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.priority = priority;
  }

  public void publish() {
    published = true;
  }

  public void unpublish() {
    published = false;
  }

  /** 表示対象の判定(公開中、かつ配信期間内)。管理画面のプレビュー表示等でも同じ基準を使う。 */
  public boolean isActiveAt(LocalDateTime at) {
    return published && !startsAt.isAfter(at) && (endsAt == null || endsAt.isAfter(at));
  }

  /** 送信した管理者が退会した場合に、作成者情報だけを外す(お知らせ本体・表示記録は残す)。 */
  public void clearCreatedByAdmin() {
    createdByAdmin = null;
  }
}
