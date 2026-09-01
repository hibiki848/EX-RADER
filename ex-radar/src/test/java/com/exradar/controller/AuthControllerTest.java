package com.exradar.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.*;
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
class AuthControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  @Test
  void publicPagesAreAccessible() throws Exception {
    mvc.perform(get("/register")).andExpect(status().isOk());
    mvc.perform(get("/login")).andExpect(status().isOk());
  }

  @Test
  void registrationRedirectsAndPersists() throws Exception {
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("email", "web@example.com")
                .param("displayName", "Webユーザー")
                .param("password", "password123")
                .param("passwordConfirmation", "password123"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
    org.assertj.core.api.Assertions.assertThat(users.findByEmailIgnoreCase("web@example.com"))
        .isPresent();
  }

  @Test
  void invalidRegistrationShowsErrors() throws Exception {
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("email", "bad")
                .param("displayName", "")
                .param("password", "short")
                .param("passwordConfirmation", "other"))
        .andExpect(status().isOk())
        .andExpect(
            model()
                .attributeHasFieldErrors(
                    "registrationForm",
                    "email",
                    "displayName",
                    "password",
                    "passwordConfirmation"));
  }

  @Test
  void protectedPageRequiresLogin() throws Exception {
    mvc.perform(get("/mypage")).andExpect(status().is3xxRedirection());
  }

  @Test
  void registrationRequiresCsrf() throws Exception {
    mvc.perform(post("/register")).andExpect(status().isForbidden());
  }

  @Test
  void activeUserCanLoginButSuspendedUserCannot() throws Exception {
    users.save(new User("active@example.com", encoder.encode("password123"), "Active", Role.USER));
    var suspended =
        new User("stopped@example.com", encoder.encode("password123"), "Stopped", Role.USER);
    suspended.setSuspended(true);
    users.save(suspended);
    mvc.perform(formLogin().user("active@example.com").password("password123"))
        .andExpect(authenticated());
    mvc.perform(formLogin().user("stopped@example.com").password("password123"))
        .andExpect(unauthenticated());
  }
}
