package com.exradar.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.config.SecurityConfig;
import com.exradar.dto.*;
import com.exradar.entity.*;
import com.exradar.repository.*;
import com.exradar.service.ExperiencePostService;
import com.exradar.service.InteractionService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ExperiencePostController.class, PublicProfileController.class})
@Import(SecurityConfig.class)
class PublicDiscoveryControllerTest {
  @MockBean InteractionService interactions;
  @Autowired MockMvc mvc;
  @MockBean ExperiencePostService service;
  @MockBean CategoryRepository categories;
  @MockBean PersonalValueRepository personalValues;
  @MockBean UserRepository users;

  @Test
  void anonymousCanSearchWithPagingAndAllFilters() throws Exception {
    when(categories.findByActiveTrueOrderByDisplayOrder()).thenReturn(List.of());
    when(service.search(any(), eq(1), eq("popular"))).thenReturn(Page.empty(PageRequest.of(1, 12)));
    mvc.perform(
            get("/experiences")
                .param("page", "1")
                .param("sort", "popular")
                .param("keyword", "転職")
                .param("categoryId", "3")
                .param("tag", "IT")
                .param("ageFrom", "20")
                .param("ageTo", "39")
                .param("currentAgeGroup", "30代")
                .param("education", "大学")
                .param("occupation", "会社員")
                .param("satisfactionMin", "7")
                .param("regretMax", "4")
                .param("yearsMin", "1")
                .param("yearsMax", "10")
                .param("chooseAgain", "true"))
        .andExpect(status().isOk())
        .andExpect(view().name("experiences/list"))
        .andExpect(model().attributeExists("result"));
    verify(service)
        .search(
            argThat(
                c ->
                    "転職".equals(c.keyword())
                        && Long.valueOf(3).equals(c.categoryId())
                        && Boolean.TRUE.equals(c.chooseAgain())),
            eq(1),
            eq("popular"));
  }

  @Test
  void anonymousCanOpenProfile() throws Exception {
    var user = new User("public@example.com", "x", "公開者", Role.USER);
    when(users.findById(7L)).thenReturn(Optional.of(user));
    when(service.byAuthor(7L, 0)).thenReturn(Page.empty());
    mvc.perform(get("/profiles/7"))
        .andExpect(status().isOk())
        .andExpect(view().name("profiles/detail"));
  }

  @Test
  void anonymousIsSentToGiveToGetBeforeReadingExperience() throws Exception {
    mvc.perform(get("/experiences/10"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/experiences/unlock"));
    verify(service, never()).getVisible(anyLong(), any());
  }
}
