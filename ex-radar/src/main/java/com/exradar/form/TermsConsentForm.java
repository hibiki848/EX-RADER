package com.exradar.form;

import jakarta.validation.constraints.AssertTrue;

public class TermsConsentForm {
  @AssertTrue(message = "利用を開始するには利用規約およびプライバシーポリシーへの同意が必要です。")
  private boolean agreedToTerms;

  public boolean isAgreedToTerms() {
    return agreedToTerms;
  }

  public void setAgreedToTerms(boolean v) {
    agreedToTerms = v;
  }
}
