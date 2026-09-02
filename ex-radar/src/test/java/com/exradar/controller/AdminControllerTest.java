package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired ExperiencePostRepository posts;
  @Autowired PasswordEncoder encoder;

  @Test
  void regularUserCannotOpenAdminDashboard() throws Exception {
    mvc.perform(get("/admin").with(user("user@example.com").roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanSeeDashboardAndPromoteUser() throws Exception {
    User admin = users.save(new User("admin@example.com", encoder.encode("password123"), "Admin", Role.ADMIN));
    User target = users.save(new User("target@example.com", encoder.encode("password123"), "Target", Role.USER));

    mvc.perform(get("/admin").with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/dashboard"))
        .andExpect(model().attribute("userCount", 2L));

    mvc.perform(post("/admin/users/{id}/role", target.getId()).with(user(admin.getEmail()).roles("ADMIN")).with(csrf()).param("role", "ADMIN"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin"));

    assertThat(users.findById(target.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
  }

  @Test
  void adminCanToggleAnalyticsExclusionForRegularUser() throws Exception {
    User admin =
        users.save(new User("admin-ga@example.com", encoder.encode("password123"), "Admin", Role.ADMIN));
    User target =
        users.save(new User("operator-account@example.com", encoder.encode("password123"), "Operator", Role.USER));
    assertThat(target.isAnalyticsExcluded()).isFalse();

    mvc.perform(
            post("/admin/users/{id}/analytics-exclusion", target.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("excluded", "true"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin"));
    assertThat(users.findById(target.getId()).orElseThrow().isAnalyticsExcluded()).isTrue();

    // 通常ユーザーへ戻すと再びfalseになる
    mvc.perform(
            post("/admin/users/{id}/analytics-exclusion", target.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("excluded", "false"))
        .andExpect(status().is3xxRedirection());
    assertThat(users.findById(target.getId()).orElseThrow().isAnalyticsExcluded()).isFalse();
  }

  @Test
  void adminCanOpenSelectedUsersPosts() throws Exception {
    User admin = users.save(new User("admin-detail@example.com", encoder.encode("password123"), "Admin", Role.ADMIN));
    User target = users.save(new User("target-detail@example.com", encoder.encode("password123"), "Target", Role.USER));

    mvc.perform(get("/admin/users/{id}", target.getId()).with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/dashboard"))
        .andExpect(model().attribute("selectedUser", target));
  }
}
