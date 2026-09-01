package com.exradar.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "personal_values")
public class PersonalValue extends BaseEntity {
  @Column(nullable = false, unique = true, length = 40)
  private String name;

  @Column(nullable = false)
  private int displayOrder;

  protected PersonalValue() {}

  public String getName() {
    return name;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }
}
