package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountDeletionTest {
  @Autowired AccountService service;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  @Test
  void rejectsDeletionWithWrongPassword() {
    var user = users.save(new User("delete@example.com", encoder.encode("password123"), "削除対象", Role.USER));

    assertThatThrownBy(() -> service.deleteAccount(user.getEmail(), "wrong-password"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("現在のパスワードが正しくありません");
    assertThat(users.findById(user.getId())).isPresent();
  }

  @Test
  void deletesAccountWithCorrectPassword() {
    var user = users.save(new User("delete@example.com", encoder.encode("password123"), "削除対象", Role.USER));

    service.deleteAccount(user.getEmail(), "password123");

    assertThat(users.findByEmailIgnoreCase(user.getEmail())).isEmpty();
  }
}
