package com.exradar.form;

import jakarta.validation.constraints.*;

public class PasswordChangeForm {
  @NotBlank(message = "現在のパスワードを入力してください")
  private String currentPassword;

  @NotBlank(message = "新しいパスワードを入力してください")
  @Size(min = 8, max = 72, message = "パスワードは8〜72文字で入力してください")
  private String newPassword;

  @NotBlank(message = "確認用パスワードを入力してください")
  private String confirmation;

  public String getCurrentPassword() {
    return currentPassword;
  }

  public void setCurrentPassword(String v) {
    currentPassword = v;
  }

  public String getNewPassword() {
    return newPassword;
  }

  public void setNewPassword(String v) {
    newPassword = v;
  }

  public String getConfirmation() {
    return confirmation;
  }

  public void setConfirmation(String v) {
    confirmation = v;
  }
}
