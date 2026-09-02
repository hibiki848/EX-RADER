package com.exradar.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.ReactionType;
import com.exradar.form.CommentForm;
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

  @Test
  @WithMockUser(username = "commenter@example.com")
  void validCommentIsSavedViaServiceAndRedirectsWithSuccessMessage() throws Exception {
    mvc.perform(post("/experiences/1/comments").with(csrf()).param("body", "参考になりました"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/experiences/1#comments"))
        .andExpect(flash().attribute("successMessage", "コメントを投稿しました"));

    var captor = org.mockito.ArgumentCaptor.forClass(CommentForm.class);
    verify(service).addComment(eq(1L), captor.capture(), eq("commenter@example.com"));
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getBody()).isEqualTo("参考になりました");
  }

  @Test
  @WithMockUser(username = "owner@example.com")
  void deletingCommentCallsServiceWithCorrectIdsAndRedirects() throws Exception {
    mvc.perform(post("/experiences/1/comments/5/delete").with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/experiences/1#comments"))
        .andExpect(flash().attribute("successMessage", "コメントを削除しました"));

    verify(service).deleteComment(1L, 5L, "owner@example.com");
  }

  @Test
  @WithMockUser(username = "reader@example.com")
  void deleteCommentRequiresCsrf() throws Exception {
    mvc.perform(post("/experiences/1/comments/5/delete")).andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  @WithMockUser(username = "reporter@example.com")
  void reportingExperiencePostCallsServiceAndRedirectsToPostReportAnchor() throws Exception {
    mvc.perform(
            post("/reports")
                .with(csrf())
                .param("targetType", "EXPERIENCE_POST")
                .param("targetId", "7")
                .param("reason", "不適切な内容が含まれています"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/experiences/7#report"))
        .andExpect(flash().attribute("successMessage", "通報を受け付けました"));

    verify(service).report(any(), eq("reporter@example.com"));
  }

  @Test
  @WithMockUser(username = "reporter@example.com")
  void reportingCommentRedirectsToCommentsAnchor() throws Exception {
    mvc.perform(
            post("/reports")
                .with(csrf())
                .param("targetType", "COMMENT")
                .param("targetId", "9")
                .param("reason", "不適切なコメントです"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/experiences/9#comments"));

    verify(service).report(any(), eq("reporter@example.com"));
  }

  @Test
  @WithMockUser(username = "reporter@example.com")
  void blankReportReasonIsRejectedWithoutCallingService() throws Exception {
    mvc.perform(
            post("/reports")
                .with(csrf())
                .param("targetType", "EXPERIENCE_POST")
                .param("targetId", "7")
                .param("reason", " "))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attributeExists("org.springframework.validation.BindingResult.reportForm"));
    verifyNoInteractions(service);
  }
}
