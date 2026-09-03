package com.exradar.entity;

import jakarta.persistence.*;

/**
 * ユーザーが体験談を最後まで読んだことの記録。(user_id, post_id)一意で、
 * 同じ体験談を何度読んでも行は増えない。createdAt=初回既読、updatedAt=最終アクセス時点
 * (BaseEntityの標準カラムを流用。現状は初回作成時のみ書き込み、更新はしない)。
 */
@Entity
@Table(
    name = "experience_reads",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_experience_read_user_post", columnNames = {"user_id", "post_id"}))
public class ExperienceRead extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "post_id")
  private ExperiencePost post;

  protected ExperienceRead() {}

  public ExperienceRead(User user, ExperiencePost post) {
    this.user = user;
    this.post = post;
  }

  public User getUser() {
    return user;
  }

  public ExperiencePost getPost() {
    return post;
  }
}
