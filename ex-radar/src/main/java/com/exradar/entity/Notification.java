package com.exradar.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User recipient;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private NotificationType type;

  @Column(nullable = false, length = 200)
  private String message;

  private Long referenceId;

  @Column(nullable = false)
  private boolean readFlag;

  protected Notification() {}

  public Notification(User recipient, NotificationType type, String message, Long referenceId) {
    this.recipient = recipient;
    this.type = type;
    this.message = message;
    this.referenceId = referenceId;
  }

  public User getRecipient() {
    return recipient;
  }

  public NotificationType getType() {
    return type;
  }

  public String getMessage() {
    return message;
  }

  public Long getReferenceId() {
    return referenceId;
  }

  public boolean isReadFlag() {
    return readFlag;
  }

  public void markRead() {
    readFlag = true;
  }
}
