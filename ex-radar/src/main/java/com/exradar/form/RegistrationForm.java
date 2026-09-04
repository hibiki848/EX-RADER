package com.exradar.form;

import jakarta.validation.constraints.*;

public class RegistrationForm {
  @NotBlank(message = "メールアドレスを入力してください")
  @Email(message = "メールアドレスの形式が正しくありません")
  @Size(max = 254)
  private String email;

  @NotBlank(message = "表示名を入力してください")
  @Size(max = 50, message = "表示名は50文字以内で入力してください")
  private String displayName;

  @NotBlank(message = "パスワードを入力してください")
  @Size(min = 8, max = 72, message = "パスワードは8〜72文字で入力してください")
  private String password;

  @NotBlank(message = "確認用パスワードを入力してください")
  private String passwordConfirmation;

  @AssertTrue(message = "登録するには利用規約およびプライバシーポリシーへの同意が必要です。")
  private boolean agreedToTerms;

  public String getEmail() {
    return email;
  }

  public void setEmail(String v) {
    email = v;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String v) {
    displayName = v;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String v) {
    password = v;
  }

  public String getPasswordConfirmation() {
    return passwordConfirmation;
  }

  public void setPasswordConfirmation(String v) {
    passwordConfirmation = v;
  }

  public boolean isAgreedToTerms() {
    return agreedToTerms;
  }

  public void setAgreedToTerms(boolean v) {
    agreedToTerms = v;
  }
}
