package com.exradar.form;

import com.exradar.entity.ExperiencePost;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public class ExperiencePostForm {
  @NotNull(message = "カテゴリを選択してください")
  private Long categoryId;

  @NotBlank(message = "タイトルを入力してください")
  @Size(max = 150, message = "タイトルは150文字以内で入力してください")
  private String title;

  @Min(value = 0, message = "選択時の年齢は0以上で入力してください")
  @Max(value = 120, message = "選択時の年齢は120以下で入力してください")
  private Integer ageAtChoice;

  @Size(max = 100, message = "当時の立場は100文字以内で入力してください")
  private String statusAtChoice;

  @Size(max = 20, message = "現在の年代は20文字以内で入力してください")
  private String currentAgeGroup;

  @Min(value = 0, message = "経過年数は0以上で入力してください")
  @Max(value = 100, message = "経過年数は100以下で入力してください")
  private Integer yearsElapsed;

  @NotBlank(message = "選択前の状況を入力してください")
  @Size(max = 3000, message = "選択前の状況は3000文字以内で入力してください")
  private String situationBefore;

  @NotBlank(message = "当時の悩みを入力してください")
  @Size(max = 3000, message = "当時の悩みは3000文字以内で入力してください")
  private String worries;

  @NotBlank(message = "検討した選択肢を入力してください")
  @Size(max = 3000, message = "検討した選択肢は3000文字以内で入力してください")
  private String alternatives;

  @NotBlank(message = "実際に選んだことを入力してください")
  @Size(max = 3000, message = "実際に選んだことは3000文字以内で入力してください")
  private String choiceMade;

  @NotBlank(message = "選んだ理由を入力してください")
  @Size(max = 3000, message = "選んだ理由は3000文字以内で入力してください")
  private String reason;

  @NotBlank(message = "その後の結果を入力してください")
  @Size(max = 5000, message = "その後の結果は5000文字以内で入力してください")
  private String outcome;

  @NotBlank(message = "良かったことを入力してください")
  @Size(max = 3000, message = "良かったことは3000文字以内で入力してください")
  private String goodThings;

  @NotBlank(message = "大変だったことを入力してください")
  @Size(max = 3000, message = "大変だったことは3000文字以内で入力してください")
  private String difficulties;

  @NotBlank(message = "想定外だったことを入力してください")
  @Size(max = 3000, message = "想定外だったことは3000文字以内で入力してください")
  private String unexpectedThings;

  @Size(max = 3000)
  private String decisionCriteria;

  @Size(max = 5000)
  private String learned;

  @Size(max = 3000)
  private String wishKnown;

  @Size(max = 3000)
  private String unexpectedlyOkay;

  @Size(max = 3000)
  private String preparationHelped;

  @Size(max = 3000)
  private String missedRegret;

  @Size(max = 3000)
  private String lesson;

  @Size(max = 3000)
  private String suitableFor;

  @Size(max = 3000)
  private String cautionFor;

  private Set<Long> valueIds = new LinkedHashSet<>();

  @NotNull(message = "満足度を選択してください")
  @Min(value = 1, message = "満足度は1〜10で選択してください")
  @Max(value = 10, message = "満足度は1〜10で選択してください")
  private Integer satisfaction;

  @NotNull(message = "後悔度を選択してください")
  @Min(value = 1, message = "後悔度は1〜10で選択してください")
  @Max(value = 10, message = "後悔度は1〜10で選択してください")
  private Integer regret;

  private boolean chooseAgain;

  @NotBlank(message = "過去の自分へのアドバイスを入力してください")
  @Size(max = 3000, message = "アドバイスは3000文字以内で入力してください")
  private String adviceToPastSelf;

  private boolean published = true;

  @Size(max = 300, message = "タグは合計300文字以内で入力してください")
  @Pattern(
      regexp = "^$|^(\\s*#?[^,、]{1,50}\\s*)([,、]\\s*#?[^,、]{1,50}\\s*){0,9}$",
      message = "タグは1個50文字以内、10個までカンマ区切りで入力してください")
  private String tagNames;

  @Valid
  @Size(max = 20, message = "人生イベントは20件以内で入力してください")
  private List<LifeEventForm> lifeEvents = new ArrayList<>();

  public static ExperiencePostForm from(ExperiencePost p) {
    var f = new ExperiencePostForm();
    f.categoryId = p.getCategory().getId();
    f.title = p.getTitle();
    f.ageAtChoice = p.getAgeAtChoice();
    f.statusAtChoice = p.getStatusAtChoice();
    f.currentAgeGroup = p.getCurrentAgeGroup();
    f.yearsElapsed = p.getYearsElapsed();
    f.situationBefore = p.getSituationBefore();
    f.worries = p.getWorries();
    f.alternatives = p.getAlternatives();
    f.choiceMade = p.getChoiceMade();
    f.reason = p.getReason();
    f.outcome = p.getOutcome();
    f.goodThings = p.getGoodThings();
    f.difficulties = p.getDifficulties();
    f.unexpectedThings = p.getUnexpectedThings();
    f.decisionCriteria = p.getDecisionCriteria();
    f.learned = p.getLearned();
    f.wishKnown = p.getWishKnown();
    f.unexpectedlyOkay = p.getUnexpectedlyOkay();
    f.preparationHelped = p.getPreparationHelped();
    f.missedRegret = p.getMissedRegret();
    f.lesson = p.getLesson();
    f.suitableFor = p.getSuitableFor();
    f.cautionFor = p.getCautionFor();
    p.getValues().forEach(v -> f.valueIds.add(v.getId()));
    f.satisfaction = p.getSatisfaction();
    f.regret = p.getRegret();
    f.chooseAgain = p.isChooseAgain();
    f.adviceToPastSelf = p.getAdviceToPastSelf();
    f.published = p.isPublished();
    for (var e : p.getLifeEvents()) {
      var le = new LifeEventForm();
      le.setAgeLabel(e.getAgeLabel());
      le.setTitle(e.getTitle());
      le.setDescription(e.getDescription());
      f.lifeEvents.add(le);
    }
    return f;
  }

  public Long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(Long v) {
    categoryId = v;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public Integer getAgeAtChoice() {
    return ageAtChoice;
  }

  public void setAgeAtChoice(Integer v) {
    ageAtChoice = v;
  }

  public String getStatusAtChoice() {
    return statusAtChoice;
  }

  public void setStatusAtChoice(String v) {
    statusAtChoice = v;
  }

  public String getCurrentAgeGroup() {
    return currentAgeGroup;
  }

  public void setCurrentAgeGroup(String v) {
    currentAgeGroup = v;
  }

  public Integer getYearsElapsed() {
    return yearsElapsed;
  }

  public void setYearsElapsed(Integer v) {
    yearsElapsed = v;
  }

  public String getSituationBefore() {
    return situationBefore;
  }

  public void setSituationBefore(String v) {
    situationBefore = v;
  }

  public String getWorries() {
    return worries;
  }

  public void setWorries(String v) {
    worries = v;
  }

  public String getAlternatives() {
    return alternatives;
  }

  public void setAlternatives(String v) {
    alternatives = v;
  }

  public String getChoiceMade() {
    return choiceMade;
  }

  public void setChoiceMade(String v) {
    choiceMade = v;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String v) {
    reason = v;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String v) {
    outcome = v;
  }

  public String getGoodThings() {
    return goodThings;
  }

  public void setGoodThings(String v) {
    goodThings = v;
  }

  public String getDifficulties() {
    return difficulties;
  }

  public void setDifficulties(String v) {
    difficulties = v;
  }

  public String getUnexpectedThings() {
    return unexpectedThings;
  }

  public void setUnexpectedThings(String v) {
    unexpectedThings = v;
  }

  public String getDecisionCriteria() {
    return this.decisionCriteria;
  }

  public void setDecisionCriteria(String decisionCriteria) {
    this.decisionCriteria = decisionCriteria;
  }

  public String getLearned() {
    return learned;
  }

  public void setLearned(String v) {
    learned = v;
  }

  public String getWishKnown() {
    return wishKnown;
  }

  public void setWishKnown(String v) {
    wishKnown = v;
  }

  public String getUnexpectedlyOkay() {
    return unexpectedlyOkay;
  }

  public void setUnexpectedlyOkay(String v) {
    unexpectedlyOkay = v;
  }

  public String getPreparationHelped() {
    return preparationHelped;
  }

  public void setPreparationHelped(String v) {
    preparationHelped = v;
  }

  public String getMissedRegret() {
    return missedRegret;
  }

  public void setMissedRegret(String v) {
    missedRegret = v;
  }

  public String getLesson() {
    return lesson;
  }

  public void setLesson(String v) {
    lesson = v;
  }

  public String getSuitableFor() {
    return suitableFor;
  }

  public void setSuitableFor(String v) {
    suitableFor = v;
  }

  public String getCautionFor() {
    return cautionFor;
  }

  public void setCautionFor(String v) {
    cautionFor = v;
  }

  public Set<Long> getValueIds() {
    return valueIds;
  }

  public void setValueIds(Set<Long> v) {
    valueIds = v == null ? new LinkedHashSet<>() : v;
  }

  public Integer getSatisfaction() {
    return satisfaction;
  }

  public void setSatisfaction(Integer v) {
    satisfaction = v;
  }

  public Integer getRegret() {
    return regret;
  }

  public void setRegret(Integer v) {
    regret = v;
  }

  public boolean isChooseAgain() {
    return chooseAgain;
  }

  public void setChooseAgain(boolean v) {
    chooseAgain = v;
  }

  public String getAdviceToPastSelf() {
    return adviceToPastSelf;
  }

  public void setAdviceToPastSelf(String v) {
    adviceToPastSelf = v;
  }

  public boolean isPublished() {
    return published;
  }

  public void setPublished(boolean v) {
    published = v;
  }

  public List<LifeEventForm> getLifeEvents() {
    return lifeEvents;
  }

  public void setLifeEvents(List<LifeEventForm> v) {
    lifeEvents = v == null ? new ArrayList<>() : v;
  }

  public String getTagNames() {
    return tagNames;
  }

  public void setTagNames(String v) {
    tagNames = v;
  }
}
