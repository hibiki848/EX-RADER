package com.exradar.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "tags",
    uniqueConstraints = @UniqueConstraint(name = "uk_tags_name", columnNames = "name"))
public class Tag extends BaseEntity {
  @Column(nullable = false, length = 50)
  private String name;

  protected Tag() {}

  public Tag(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
