package com.exradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "articles",
    uniqueConstraints = @UniqueConstraint(name = "uk_articles_slug", columnNames = "slug"))
public class Article extends BaseEntity {
  @Column(nullable = false, length = 150)
  private String title;

  @Column(nullable = false, length = 80)
  private String slug;

  @Column(nullable = false, length = 300)
  private String description;

  @Column(nullable = false, length = 20000)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ArticleStatus status = ArticleStatus.DRAFT;

  private LocalDateTime publishedAt;

  protected Article() {}

  public Article(String title, String slug, String description, String content) {
    this.title = title;
    this.slug = slug;
    this.description = description;
    this.content = content;
  }

  public String getTitle() {
    return title;
  }

  public String getSlug() {
    return slug;
  }

  public String getDescription() {
    return description;
  }

  public String getContent() {
    return content;
  }

  public ArticleStatus getStatus() {
    return status;
  }

  public boolean isPublished() {
    return status == ArticleStatus.PUBLISHED;
  }

  public LocalDateTime getPublishedAt() {
    return publishedAt;
  }

  public void update(String title, String slug, String description, String content) {
    this.title = title;
    this.slug = slug;
    this.description = description;
    this.content = content;
  }

  public void publish() {
    if (publishedAt == null) publishedAt = LocalDateTime.now();
    status = ArticleStatus.PUBLISHED;
  }

  public void unpublish() {
    status = ArticleStatus.DRAFT;
  }
}
