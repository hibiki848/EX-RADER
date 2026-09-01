package com.exradar.config;

import com.exradar.entity.Category;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.CategoryRepository;
import com.exradar.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
public class DevDataInitializer {
  @Bean
  CommandLineRunner sampleData(
      UserRepository users, CategoryRepository categories, PasswordEncoder encoder) {
    return args -> {
      String[][] values = {
        {"勉強", "study"},
        {"高校進学", "high-school"},
        {"大学進学", "university"},
        {"専門学校", "vocational-school"},
        {"高卒就職", "work-after-high-school"},
        {"大学中退", "university-dropout"},
        {"就職", "employment"},
        {"転職", "career-change"},
        {"異業種転職", "career-change-industry"},
        {"公務員", "public-servant"},
        {"資格取得", "qualification"},
        {"上京", "move-to-tokyo"},
        {"地元就職", "local-employment"},
        {"地元へ戻る", "return-home"},
        {"フリーランス", "freelance"}
      };

      for (int i = 0; i < values.length; i++) {
        String name = values[i][0];
        String slug = values[i][1];
        if (categories.findBySlug(slug).isEmpty()) {
          try {
            categories.save(new Category(name, slug, i));
          } catch (DataIntegrityViolationException ignored) {
            // Already present in a reused local H2 database.
          }
        }
      }

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
