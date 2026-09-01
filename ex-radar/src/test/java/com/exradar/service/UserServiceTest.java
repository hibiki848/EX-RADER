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
  void rejectsDuplicateEmailIgnoringCase() {
    service.register(form("same@example.com"));
    assertThatThrownBy(() -> service.register(form("SAME@example.com")))
        .isInstanceOf(DuplicateEmailException.class);
  }
}
