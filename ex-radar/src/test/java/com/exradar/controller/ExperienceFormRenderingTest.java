package com.exradar.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.*;
import com.exradar.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExperienceFormRenderingTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;

  @BeforeEach
  void createLoginUser() {
    if (users.findByEmailIgnoreCase("form@example.com").isEmpty())
      users.save(new User("form@example.com", "encoded", "フォーム確認", Role.USER));
  }

  @Test
  void loggedInUserCanRenderNewExperienceForm() throws Exception {
    mvc.perform(get("/experiences/new").with(user("form@example.com")))
        .andExpect(status().isOk())
        .andExpect(view().name("experiences/form"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("経験して分かったこと・教訓")));
  }
}
