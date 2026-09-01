package com.exradar.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "categories",
    uniqueConstraints = @UniqueConstraint(name = "uk_categories_slug", columnNames = "slug"))
public class Category extends BaseEntity {
  @Column(nullable = false, length = 60)
  private String name;

  @Column(nullable = false, length = 80)
  private String slug;

  @Column(nullable = false)
  private int displayOrder;

  @Column(nullable = false)
  private boolean active = true;

  protected Category() {}

  public Category(String name, String slug, int displayOrder) {
    this.name = name;
    this.slug = slug;
    this.displayOrder = displayOrder;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public boolean isActive() {
    return active;
  }
}
