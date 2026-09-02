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

  // どちらも未入力を許容する。画面上の記事タイトル(title)とは役割が異なり、
  // HTMLのtitleタグ・meta descriptionにのみ使う(本文中の見出し表示には使わない)。
  @Column(name = "seo_title", length = 120)
  private String seoTitle;

  @Column(name = "meta_description", length = 300)
  private String metaDescription;

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

  public String getSeoTitle() {
    return seoTitle;
  }

  public String getMetaDescription() {
    return metaDescription;
  }

  /** titleタグ用。SEOタイトルが未入力の場合は通常の記事タイトルにフォールバックする。 */
  public String getEffectiveSeoTitle() {
    return isBlank(seoTitle) ? title : seoTitle;
  }

  /**
   * meta description用。SEO用descriptionが未入力の場合は既存の概要欄(description)に
   * フォールバックする。どちらも空ならnullを返し、呼び出し側で空のmetaタグを出力しない。
   */
  public String getEffectiveMetaDescription() {
    if (!isBlank(metaDescription)) return metaDescription;
    return isBlank(description) ? null : description;
  }

  private static boolean isBlank(String v) {
    return v == null || v.isBlank();
  }

  public void update(String title, String slug, String description, String content) {
    this.title = title;
    this.slug = slug;
    this.description = description;
    this.content = content;
  }

  public void updateSeo(String seoTitle, String metaDescription) {
    this.seoTitle = seoTitle;
    this.metaDescription = metaDescription;
  }

  public void publish() {
    if (publishedAt == null) publishedAt = LocalDateTime.now();
    status = ArticleStatus.PUBLISHED;
  }

  public void unpublish() {
    status = ArticleStatus.DRAFT;
  }
}
