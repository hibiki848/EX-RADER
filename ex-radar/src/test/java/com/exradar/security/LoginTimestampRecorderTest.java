package com.exradar.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthenticationSuccessEventを購読するLoginTimestampRecorderが、実際のログイン
 * (フォームログイン)を通しても正しく発火することを確認する。ユニットテストで
 * User#recordLoginを直接呼ぶだけでは、Spring Securityのイベント発行配線自体が
 * 効いているかまでは確認できないため、MockMvcの実ログインフローで検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoginTimestampRecorderTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  @Test
  void firstSuccessfulLoginRecordsFirstAndLastLoginAt() throws Exception {
    var user =
        users.save(new User("login-record@example.com", encoder.encode("password123"), "ログイン記録確認", Role.USER));
    assertThat(user.getFirstLoginAt()).isNull();
    assertThat(user.getLastLoginAt()).isNull();

    mvc.perform(formLogin().user(user.getEmail()).password("password123")).andExpect(authenticated());

    var reloaded = users.findById(user.getId()).orElseThrow();
    assertThat(reloaded.getFirstLoginAt()).isNotNull();
    assertThat(reloaded.getLastLoginAt()).isNotNull();
    assertThat(reloaded.getLastLoginAt()).isEqualTo(reloaded.getFirstLoginAt());
  }

  @Test
  void secondLoginUpdatesLastLoginAtButKeepsFirstLoginAtUnchanged() throws Exception {
    var user =
        users.save(new User("login-record2@example.com", encoder.encode("password123"), "2回目ログイン確認", Role.USER));

    mvc.perform(formLogin().user(user.getEmail()).password("password123")).andExpect(authenticated());
    var afterFirst = users.findById(user.getId()).orElseThrow();
    var firstLoginAt = afterFirst.getFirstLoginAt();
    assertThat(firstLoginAt).isNotNull();

    mvc.perform(formLogin().user(user.getEmail()).password("password123")).andExpect(authenticated());
    var afterSecond = users.findById(user.getId()).orElseThrow();

    assertThat(afterSecond.getFirstLoginAt()).isEqualTo(firstLoginAt);
    assertThat(afterSecond.getLastLoginAt()).isNotNull();
  }

  @Test
  void failedLoginDoesNotRecordAnyTimestamp() throws Exception {
    var user =
        users.save(new User("login-fail@example.com", encoder.encode("password123"), "失敗ログイン確認", Role.USER));

    mvc.perform(formLogin().user(user.getEmail()).password("wrong-password"));

    var reloaded = users.findById(user.getId()).orElseThrow();
    assertThat(reloaded.getFirstLoginAt()).isNull();
    assertThat(reloaded.getLastLoginAt()).isNull();
  }
}
