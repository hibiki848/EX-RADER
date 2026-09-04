package com.exradar.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * お問い合わせ1件。送信者情報(name/email)は問い合わせ内容として毎回保存する
 * (userがログイン中でも、後から表示名・メールアドレスが変わる可能性があるため、
 * 送信当時の値をそのまま残す)。userはログインユーザーからの送信の場合のみ設定され、
 * 運営メッセージとの連携(このユーザーへ運営メッセージを送る)に使う。
 */
@Entity
@Table(name = "contact_inquiries")
public class ContactInquiry extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ContactCategory category;

  @Column(length = 100)
  private String name;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(nullable = false, length = 100)
  private String subject;

  @Column(nullable = false, length = 3000)
  private String body;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "related_post_id")
  private ExperiencePost relatedPost;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InquiryStatus status;

  @Column(name = "admin_memo", length = 2000)
  private String adminMemo;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  protected ContactInquiry() {}

  public ContactInquiry(
      User user,
      ContactCategory category,
      String name,
      String email,
      String subject,
      String body,
      ExperiencePost relatedPost) {
    this.user = user;
    this.category = category;
    this.name = name;
    this.email = email;
    this.subject = subject;
    this.body = body;
    this.relatedPost = relatedPost;
    this.status = InquiryStatus.NEW;
  }

  public User getUser() {
    return user;
  }

  public ContactCategory getCategory() {
    return category;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getSubject() {
    return subject;
  }

  public String getBody() {
    return body;
  }

  public ExperiencePost getRelatedPost() {
    return relatedPost;
  }

  public InquiryStatus getStatus() {
    return status;
  }

  public String getAdminMemo() {
    return adminMemo;
  }

  public LocalDateTime getResolvedAt() {
    return resolvedAt;
  }

  /**
   * ステータス変更。RESOLVEDへ変わった瞬間にresolvedAtを記録し、RESOLVED以外へ戻した場合は
   * 再度未解決に戻ったことを一貫して表せるようresolvedAtをクリアする
   * (再度RESOLVEDにすれば、その時点の新しい日時が改めて記録される)。
   */
  public void changeStatus(InquiryStatus newStatus, LocalDateTime at) {
    status = newStatus;
    resolvedAt = newStatus == InquiryStatus.RESOLVED ? at : null;
  }

  public void updateAdminMemo(String memo) {
    adminMemo = memo;
  }
}
