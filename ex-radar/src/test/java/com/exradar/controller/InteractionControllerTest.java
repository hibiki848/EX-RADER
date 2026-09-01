package com.exradar.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.ReactionType;
import com.exradar.service.InteractionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InteractionController.class)
class InteractionControllerTest {
  @Autowired MockMvc mvc;
  @MockBean InteractionService service;

  @Test
  @WithMockUser(username = "reader@example.com")
  void reactionRequiresCsrf() throws Exception {
    mvc.perform(post("/experiences/1/reactions/HELPFUL")).andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  @WithMockUser(username = "reader@example.com")
  void togglesReactionWithCsrf() throws Exception {
    when(service.toggleReaction(1L, ReactionType.HELPFUL, "reader@example.com")).thenReturn(true);
    mvc.perform(post("/experiences/1/reactions/HELPFUL").with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/experiences/1#reactions"));
    verify(service).toggleReaction(1L, ReactionType.HELPFUL, "reader@example.com");
  }

  @Test
  @WithMockUser(username = "reader@example.com")
  void rejectsBlankCommentInJapanese() throws Exception {
    mvc.perform(post("/experiences/1/comments").with(csrf()).param("body", " "))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            flash().attributeExists("org.springframework.validation.BindingResult.commentForm"));
    verifyNoInteractions(service);
  }
}
