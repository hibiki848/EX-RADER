package com.exradar.form;

import jakarta.validation.constraints.*;

public class ProfileForm {
  @NotBlank(message = "表示名を入力してください")
  @Size(max = 50, message = "表示名は50文字以内で入力してください")
  private String displayName;

  @Size(max = 20, message = "年代は20文字以内で入力してください")
  private String ageGroup;

  @Size(max = 100, message = "学歴は100文字以内で入力してください")
  private String education;

  @Size(max = 100, message = "職業は100文字以内で入力してください")
  private String occupation;

  @Size(max = 20, message = "都道府県は20文字以内で入力してください")
  private String prefecture;

  @Size(max = 1000, message = "自己紹介は1000文字以内で入力してください")
  private String biography;

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String v) {
    displayName = v;
  }

  public String getAgeGroup() {
    return ageGroup;
  }

  public void setAgeGroup(String v) {
    ageGroup = v;
  }

  public String getEducation() {
    return education;
  }

  public void setEducation(String v) {
    education = v;
  }

  public String getOccupation() {
    return occupation;
  }

  public void setOccupation(String v) {
    occupation = v;
  }

  public String getPrefecture() {
    return prefecture;
  }

  public void setPrefecture(String v) {
    prefecture = v;
  }

  public String getBiography() {
    return biography;
  }

  public void setBiography(String v) {
    biography = v;
  }
}
