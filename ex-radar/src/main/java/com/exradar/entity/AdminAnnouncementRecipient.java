package com.exradar.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * AdminAnnouncementの対象者1人分のモーダル表示状態。(announcement, user)にUNIQUE制約があり、
 * 同一お知らせが同じユーザーへ重複登録されることはない。
 * firstDisplayedAt/lastDisplayedAt/displayCountは、モーダルを実際にユーザーへ返した時点
 * (NavigationAdvice参照)でサーバー側が記録する。未読率・開封率・表示回数を後から
 * 分析できるようにするため、単なる既読フラグではなく実際の日時・回数を保持する。
 * dismissedPermanentlyAtは「次回以降このお知らせを表示しない」を選んだ日時。
 * ユーザー全体の設定ではなく、このお知らせ1件に対する設定であることに注意。
 */
@Entity
@Table(
    name = "admin_announcement_recipients",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_admin_announcement_recipient",
            columnNames = {"announcement_id", "user_id"}))
public class AdminAnnouncementRecipient extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "announcement_id")
  private AdminAnnouncement announcement;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "first_displayed_at")
  private LocalDateTime firstDisplayedAt;

  @Column(name = "last_displayed_at")
  private LocalDateTime lastDisplayedAt;

  @Column(name = "display_count", nullable = false)
  private int displayCount;

  @Column(name = "dismissed_permanently_at")
  private LocalDateTime dismissedPermanentlyAt;

  protected AdminAnnouncementRecipient() {}

  public AdminAnnouncementRecipient(AdminAnnouncement announcement, User user) {
    this.announcement = announcement;
    this.user = user;
    this.displayCount = 0;
  }

  public AdminAnnouncement getAnnouncement() {
    return announcement;
  }

  public User getUser() {
    return user;
  }

  public LocalDateTime getFirstDisplayedAt() {
    return firstDisplayedAt;
  }

  public LocalDateTime getLastDisplayedAt() {
    return lastDisplayedAt;
  }

  public int getDisplayCount() {
    return displayCount;
  }

  public LocalDateTime getDismissedPermanentlyAt() {
    return dismissedPermanentlyAt;
  }

  public boolean isDismissedPermanently() {
    return dismissedPermanentlyAt != null;
  }

  /** モーダルを実際にユーザーへ返した時点で呼ぶ。初回表示日時は保持したまま、最終表示日時と回数を更新する。 */
  public void recordDisplay(LocalDateTime at) {
    if (firstDisplayedAt == null) firstDisplayedAt = at;
    lastDisplayedAt = at;
    displayCount++;
  }

  /** 「次回以降表示しない」。同じお知らせを何度dismissしても状態がおかしくならないよう冪等にする。 */
  public void dismissPermanently(LocalDateTime at) {
    if (dismissedPermanentlyAt == null) dismissedPermanentlyAt = at;
  }
}
