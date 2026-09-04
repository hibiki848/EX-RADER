package com.exradar.service;

import static org.assertj.core.api.Assertions.*;

import com.exradar.exception.DuplicateEmailException;
import com.exradar.form.RegistrationForm;
import com.exradar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {
  @Autowired UserService service;
  @Autowired UserRepository users;

  private RegistrationForm form(String email) {
    var f = new RegistrationForm();
    f.setEmail(email);
    f.setDisplayName("テストユーザー");
    f.setPassword("password123");
    f.setPasswordConfirmation("password123");
    f.setAgreedToTerms(true);
    return f;
  }

  @Test
  void registersWithHashedPassword() {
    var saved = service.register(form("new@example.com"));
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getPassword()).doesNotContain("password123");
    assertThat(users.findByEmailIgnoreCase("NEW@example.com")).isPresent();
  }

  @Test
  void recordsTermsAndPrivacyPolicyAgreementTimestampOnRegistration() {
    var saved = service.register(form("agreement@example.com"));
    assertThat(saved.getTermsAgreedAt()).isNotNull();
    assertThat(saved.getPrivacyPolicyAgreedAt()).isNotNull();
  }

  @Test
  void rejectsRegistrationWithoutAgreeingToTermsEvenIfCalledDirectly() {
    var f = form("no-agreement@example.com");
    f.setAgreedToTerms(false);
    assertThatThrownBy(() -> service.register(f))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("登録するには利用規約およびプライバシーポリシーへの同意が必要です。");
    assertThat(users.findByEmailIgnoreCase("no-agreement@example.com")).isEmpty();
  }

  @Test
  void rejectsDuplicateEmailIgnoringCase() {
    service.register(form("same@example.com"));
    assertThatThrownBy(() -> service.register(form("SAME@example.com")))
        .isInstanceOf(DuplicateEmailException.class);
  }
}
