package com.exradar.form;

import jakarta.validation.constraints.NotBlank;

public class DeleteAccountForm {
  @NotBlank(message = "現在のパスワードを入力してください")
  private String password;

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
