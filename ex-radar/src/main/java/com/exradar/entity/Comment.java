package com.exradar.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "comments")
public class Comment extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private ExperiencePost post;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User author;

  @Column(nullable = false, length = 2000)
  private String body;

  protected Comment() {}

  public Comment(ExperiencePost post, User author, String body) {
    this.post = post;
    this.author = author;
    this.body = body;
  }

  public ExperiencePost getPost() {
    return post;
  }

  public User getAuthor() {
    return author;
  }

  public String getBody() {
    return body;
  }
}
