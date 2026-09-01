package com.exradar.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "life_events")
public class LifeEvent extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private ExperiencePost post;

  @Column(length = 20)
  private String ageLabel;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(length = 1000)
  private String description;

  @Column(nullable = false)
  private int displayOrder;

  protected LifeEvent() {}

  public LifeEvent(String ageLabel, String title, String description, int displayOrder) {
    this.ageLabel = ageLabel;
    this.title = title;
    this.description = description;
    this.displayOrder = displayOrder;
  }

  void attachTo(ExperiencePost post) {
    this.post = post;
  }

  public String getAgeLabel() {
    return ageLabel;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }
}
