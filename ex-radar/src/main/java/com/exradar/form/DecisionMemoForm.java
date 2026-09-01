package com.exradar.form;

import com.exradar.entity.DecisionMemo;
import jakarta.validation.constraints.*;
import java.util.*;

public class DecisionMemoForm {
  @NotBlank
  @Size(max = 150)
  private String title;

  @NotBlank
  @Size(max = 3000)
  private String concern;

  @NotBlank
  @Size(max = 3000)
  private String optionsText;

  @Size(max = 3000)
  private String anxieties;

  @Size(max = 3000)
  private String desiredGain;

  @Size(max = 3000)
  private String mustNotLose;

  @Size(max = 3000)
  private String canCompromise;

  @Size(max = 3000)
  private String cannotCompromise;

  @Size(max = 5000)
  private String initialThoughts;

  @Size(max = 5000)
  private String discoveries;

  @Size(max = 5000)
  private String helpfulLessons;

  @Size(max = 5000)
  private String currentThoughts;

  private Set<Long> valueIds = new LinkedHashSet<>();

  public static DecisionMemoForm from(DecisionMemo m) {
    var f = new DecisionMemoForm();
    f.title = m.getTitle();
    f.concern = m.getConcern();
    f.optionsText = m.getOptionsText();
    f.anxieties = m.getAnxieties();
    f.desiredGain = m.getDesiredGain();
    f.mustNotLose = m.getMustNotLose();
    f.canCompromise = m.getCanCompromise();
    f.cannotCompromise = m.getCannotCompromise();
    f.initialThoughts = m.getInitialThoughts();
    f.discoveries = m.getDiscoveries();
    f.helpfulLessons = m.getHelpfulLessons();
    f.currentThoughts = m.getCurrentThoughts();
    m.getValues().forEach(v -> f.valueIds.add(v.getId()));
    return f;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public String getConcern() {
    return concern;
  }

  public void setConcern(String v) {
    concern = v;
  }

  public String getOptionsText() {
    return optionsText;
  }

  public void setOptionsText(String v) {
    optionsText = v;
  }

  public String getAnxieties() {
    return anxieties;
  }

  public void setAnxieties(String v) {
    anxieties = v;
  }

  public String getDesiredGain() {
    return desiredGain;
  }

  public void setDesiredGain(String v) {
    desiredGain = v;
  }

  public String getMustNotLose() {
    return mustNotLose;
  }

  public void setMustNotLose(String v) {
    mustNotLose = v;
  }

  public String getCanCompromise() {
    return canCompromise;
  }

  public void setCanCompromise(String v) {
    canCompromise = v;
  }

  public String getCannotCompromise() {
    return cannotCompromise;
  }

  public void setCannotCompromise(String v) {
    cannotCompromise = v;
  }

  public String getInitialThoughts() {
    return initialThoughts;
  }

  public void setInitialThoughts(String v) {
    initialThoughts = v;
  }

  public String getDiscoveries() {
    return discoveries;
  }

  public void setDiscoveries(String v) {
    discoveries = v;
  }

  public String getHelpfulLessons() {
    return helpfulLessons;
  }

  public void setHelpfulLessons(String v) {
    helpfulLessons = v;
  }

  public String getCurrentThoughts() {
    return currentThoughts;
  }

  public void setCurrentThoughts(String v) {
    currentThoughts = v;
  }

  public Set<Long> getValueIds() {
    return valueIds;
  }

  public void setValueIds(Set<Long> v) {
    valueIds = v == null ? new LinkedHashSet<>() : v;
  }
}
