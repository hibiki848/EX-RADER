package com.exradar.entity;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "decision_memos")
public class DecisionMemo extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User user;

  @Column(nullable = false, length = 150)
  private String title;

  @Column(nullable = false, length = 3000)
  private String concern;

  @Column(nullable = false, length = 3000)
  private String optionsText;

  @Column(length = 3000)
  private String anxieties;

  @Column(length = 3000)
  private String desiredGain;

  @Column(length = 3000)
  private String mustNotLose;

  @Column(length = 3000)
  private String canCompromise;

  @Column(length = 3000)
  private String cannotCompromise;

  @Column(length = 5000)
  private String initialThoughts;

  @Column(length = 5000)
  private String discoveries;

  @Column(length = 5000)
  private String helpfulLessons;

  @Column(length = 5000)
  private String currentThoughts;

  @ManyToMany
  @JoinTable(
      name = "decision_memo_values",
      joinColumns = @JoinColumn(name = "memo_id"),
      inverseJoinColumns = @JoinColumn(name = "value_id"))
  private Set<PersonalValue> values = new LinkedHashSet<>();

  protected DecisionMemo() {}

  public DecisionMemo(User user) {
    this.user = user;
  }

  public void update(
      String title,
      String concern,
      String optionsText,
      String anxieties,
      String desiredGain,
      String mustNotLose,
      String canCompromise,
      String cannotCompromise,
      String initialThoughts,
      String discoveries,
      String helpfulLessons,
      String currentThoughts,
      Collection<PersonalValue> values) {
    this.title = title;
    this.concern = concern;
    this.optionsText = optionsText;
    this.anxieties = anxieties;
    this.desiredGain = desiredGain;
    this.mustNotLose = mustNotLose;
    this.canCompromise = canCompromise;
    this.cannotCompromise = cannotCompromise;
    this.initialThoughts = initialThoughts;
    this.discoveries = discoveries;
    this.helpfulLessons = helpfulLessons;
    this.currentThoughts = currentThoughts;
    this.values.clear();
    this.values.addAll(values);
  }

  public User getUser() {
    return user;
  }

  public String getTitle() {
    return title;
  }

  public String getConcern() {
    return concern;
  }

  public String getOptionsText() {
    return optionsText;
  }

  public String getAnxieties() {
    return anxieties;
  }

  public String getDesiredGain() {
    return desiredGain;
  }

  public String getMustNotLose() {
    return mustNotLose;
  }

  public String getCanCompromise() {
    return canCompromise;
  }

  public String getCannotCompromise() {
    return cannotCompromise;
  }

  public String getInitialThoughts() {
    return initialThoughts;
  }

  public String getDiscoveries() {
    return discoveries;
  }

  public String getHelpfulLessons() {
    return helpfulLessons;
  }

  public String getCurrentThoughts() {
    return currentThoughts;
  }

  public Set<PersonalValue> getValues() {
    return values;
  }
}
