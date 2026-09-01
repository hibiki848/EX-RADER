package com.exradar.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "reactions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_reaction_user_post_type",
            columnNames = {"user_id", "post_id", "type"}))
public class Reaction extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "post_id")
  private ExperiencePost post;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReactionType type;

  protected Reaction() {}

  public Reaction(User user, ExperiencePost post, ReactionType type) {
    this.user = user;
    this.post = post;
    this.type = type;
  }

  public User getUser() {
    return user;
  }

  public ExperiencePost getPost() {
    return post;
  }

  public ReactionType getType() {
    return type;
  }
}
