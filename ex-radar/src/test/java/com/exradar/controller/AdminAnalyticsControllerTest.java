package com.exradar.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminAnalyticsControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  @Test
  void anonymousCannotAccessAnalyticsApi() throws Exception {
    mvc.perform(get("/api/admin/analytics")).andExpect(status().is3xxRedirection());
  }

  @Test
  void regularUserCannotAccessAnalyticsApi() throws Exception {
    User target =
        users.save(
            new User("analytics-user@example.com", encoder.encode("password123"), "User", Role.USER));
    mvc.perform(get("/api/admin/analytics").with(user(target.getEmail()).roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminGetsCombinedDashboardEvenWhenGa4IsNotConfigured() throws Exception {
    User admin =
        users.save(
            new User(
                "analytics-admin@example.com", encoder.encode("password123"), "Admin", Role.ADMIN));
    mvc.perform(get("/api/admin/analytics").with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.database.totalUsers").exists())
        .andExpect(jsonPath("$.googleAnalytics.available").value(false))
        .andExpect(jsonPath("$.googleAnalytics.errorMessage").isNotEmpty())
        .andExpect(jsonPath("$.googleAnalytics.todayUsers").doesNotExist())
        .andExpect(jsonPath("$.funnel.newRegistrations30d").exists())
        .andExpect(jsonPath("$.funnel.visitors30d").doesNotExist());
  }
}
