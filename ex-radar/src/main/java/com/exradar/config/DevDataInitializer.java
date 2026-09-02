package com.exradar.config;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 開発環境用のサンプルユーザー作成のみを行う。
 * カテゴリのマスタデータはFlyway(V7__seed_categories.sql)が唯一の定義元であり、
 * 全環境(dev/test/prod)で共通して作成されるため、ここでは作成しない。
 */
@Configuration
@Profile("dev")
public class DevDataInitializer {
  @Bean
  CommandLineRunner sampleData(UserRepository users, PasswordEncoder encoder) {
    return args -> {
      saveUserIfMissing(users, encoder, "user@example.com", "password", "サンプルユーザー", Role.USER);
      saveUserIfMissing(users, encoder, "admin@example.com", "adminpass", "管理者", Role.ADMIN);
    };
  }

  private void saveUserIfMissing(
      UserRepository users,
      PasswordEncoder encoder,
      String email,
      String rawPassword,
      String displayName,
      Role role) {
    if (users.findByEmailIgnoreCase(email).isEmpty()) {
      try {
        users.save(new User(email, encoder.encode(rawPassword), displayName, role));
      } catch (DataIntegrityViolationException ignored) {
        // Already present in a reused local H2 database.
      }
    }
  }
}
