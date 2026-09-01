package com.exradar.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "account-web@example.com")
class AccountControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  @BeforeEach
  void setup() {
    users.save(
        new User("account-web@example.com", encoder.encode("password123"), "Webユーザー", Role.USER));
  }

  @Test
  void authenticatedUserCanOpenAccountPages() throws Exception {
    mvc.perform(get("/mypage")).andExpect(status().isOk()).andExpect(view().name("mypage"));
    mvc.perform(get("/mypage/profile"))
        .andExpect(status().isOk())
        .andExpect(view().name("account/profile"));
    mvc.perform(get("/mypage/password"))
        .andExpect(status().isOk())
        .andExpect(view().name("account/password"));
    mvc.perform(get("/mypage/notifications"))
        .andExpect(status().isOk())
        .andExpect(view().name("account/notifications"));
  }

  @Test
  void profileValidationRejectsBlankDisplayName() throws Exception {
    mvc.perform(post("/mypage/profile").with(csrf()).param("displayName", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("account/profile"))
        .andExpect(model().attributeHasFieldErrors("profileForm", "displayName"));
  }

  @Test
  void validPasswordChangeRedirects() throws Exception {
    mvc.perform(
            post("/mypage/password")
                .with(csrf())
                .param("currentPassword", "password123")
                .param("newPassword", "new-password123")
                .param("confirmation", "new-password123"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/mypage/password"));
  }

  @Test
  void accountWritesRequireCsrf() throws Exception {
    mvc.perform(post("/mypage/profile").param("displayName", "更新名"))
        .andExpect(status().isForbidden());
  }
}
