package com.exradar.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DisplayNameForm {
  @NotBlank(message = "表示名を入力してください")
  @Size(max = 50, message = "表示名は50文字以内で入力してください")
  private String displayName;

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String v) {
    displayName = v;
  }
}
