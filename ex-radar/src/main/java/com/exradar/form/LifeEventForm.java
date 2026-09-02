package com.exradar.form;

import jakarta.validation.constraints.*;

public class LifeEventForm {
  @Size(
      max = 20,
      message = "年齢・時期は20文字以内で入力してください",
      groups = {DraftValidation.class, PublishValidation.class})
  private String ageLabel;

  @NotBlank(message = "出来事のタイトルを入力してください", groups = PublishValidation.class)
  @Size(
      max = 100,
      message = "出来事のタイトルは100文字以内で入力してください",
      groups = {DraftValidation.class, PublishValidation.class})
  private String title;

  @Size(
      max = 1000,
      message = "出来事の説明は1000文字以内で入力してください",
      groups = {DraftValidation.class, PublishValidation.class})
  private String description;

  public String getAgeLabel() {
    return ageLabel;
  }

  public void setAgeLabel(String v) {
    ageLabel = v;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
  }
}
