package com.exradar.form;

import jakarta.validation.constraints.NotBlank;

public class LinkAccountForm {
  @NotBlank(message = "パスワードを入力してください")
  private String password;

  public String getPassword() {
    return password;
  }

  public void setPassword(String v) {
    password = v;
  }
}
