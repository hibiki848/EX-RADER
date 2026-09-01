package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Article;
import com.exradar.entity.ArticleStatus;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.repository.ArticleRepository;
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
class AdminArticleControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ArticleRepository articles;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  @Test
  void regularUserCannotOpenArticleAdmin() throws Exception {
    User user =
        users.save(new User("article-user@example.com", encoder.encode("password123"), "User", Role.USER));
    mvc.perform(get("/admin/articles").with(user(user.getEmail()).roles("USER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanCreateEditPublishAndDeleteArticle() throws Exception {
    User admin =
        users.save(
            new User("article-admin@example.com", encoder.encode("password123"), "Admin", Role.ADMIN));

    mvc.perform(
            post("/admin/articles")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("title", "転職で後悔しやすい7つのこと")
                .param("slug", "tenshoku-koukai")
                .param("description", "決める前に確認したいポイントをまとめました。")
                .param("content", "## 見出し\n本文。"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/articles"));

    Article created = articles.findAll().stream().findFirst().orElseThrow();
    assertThat(created.getStatus()).isEqualTo(ArticleStatus.DRAFT);
    assertThat(created.getSlug()).isEqualTo("tenshoku-koukai");

    mvc.perform(get("/admin/articles/{id}/edit", created.getId()).with(user(admin.getEmail()).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/articles/form"));

    mvc.perform(
            post("/admin/articles/{id}/publish", created.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertThat(articles.findById(created.getId()).orElseThrow().getStatus())
        .isEqualTo(ArticleStatus.PUBLISHED);
    assertThat(articles.findById(created.getId()).orElseThrow().getPublishedAt()).isNotNull();

    mvc.perform(
            post("/admin/articles/{id}/unpublish", created.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertThat(articles.findById(created.getId()).orElseThrow().getStatus()).isEqualTo(ArticleStatus.DRAFT);

    mvc.perform(
            post("/admin/articles/{id}/delete", created.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertThat(articles.findById(created.getId())).isEmpty();
  }

  @Test
  void duplicateSlugIsRejectedWithFriendlyError() throws Exception {
    User admin =
        users.save(
            new User("article-admin2@example.com", encoder.encode("password123"), "Admin", Role.ADMIN));
    articles.save(new Article("既存記事", "existing-slug", "概要", "本文"));

    mvc.perform(
            post("/admin/articles")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("title", "新しい記事")
                .param("slug", "existing-slug")
                .param("description", "概要")
                .param("content", "本文"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/articles/form"))
        .andExpect(model().attributeHasFieldErrors("articleForm", "slug"));
  }

  @Test
  void invalidSlugFormatIsRejected() throws Exception {
    User admin =
        users.save(
            new User("article-admin3@example.com", encoder.encode("password123"), "Admin", Role.ADMIN));

    mvc.perform(
            post("/admin/articles")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .with(csrf())
                .param("title", "新しい記事")
                .param("slug", "Invalid Slug!")
                .param("description", "概要")
                .param("content", "本文"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/articles/form"))
        .andExpect(model().attributeHasFieldErrors("articleForm", "slug"));
  }
}
