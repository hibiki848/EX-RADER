package com.exradar.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.exradar.entity.Article;
import com.exradar.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ArticleControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ArticleRepository articles;

  @Test
  void anonymousCanListOnlyPublishedArticles() throws Exception {
    var published = new Article("公開記事", "published-article", "概要", "本文");
    published.publish();
    articles.save(published);
    articles.save(new Article("下書き記事", "draft-article", "概要", "本文"));

    mvc.perform(get("/articles"))
        .andExpect(status().isOk())
        .andExpect(view().name("articles/list"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("公開記事")))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("下書き記事"))));
  }

  @Test
  void anonymousCanOpenPublishedArticleDetail() throws Exception {
    var article = new Article("転職で後悔しやすい7つのこと", "tenshoku-koukai", "概要文", "## 見出し\n本文の段落。");
    article.publish();
    articles.save(article);

    mvc.perform(get("/articles/tenshoku-koukai"))
        .andExpect(status().isOk())
        .andExpect(view().name("articles/detail"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("転職で後悔しやすい7つのこと")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("体験談を投稿する")));
  }

  @Test
  void draftArticleIsNotVisibleByDirectUrl() throws Exception {
    articles.save(new Article("非公開記事", "hidden-article", "概要", "本文"));

    mvc.perform(get("/articles/hidden-article")).andExpect(status().isNotFound());
  }

  @Test
  void unknownSlugReturnsNotFound() throws Exception {
    mvc.perform(get("/articles/no-such-slug")).andExpect(status().isNotFound());
  }
}
