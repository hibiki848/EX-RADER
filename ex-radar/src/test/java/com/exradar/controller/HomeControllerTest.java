package com.exradar.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

/** ホームページの「新着記事」セクションと、ヘッダーから削除した「失敗から学ぶ」リンクの検証。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HomeControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ArticleRepository articles;

  @Test
  void headerNoLongerHasFailureLearningLink() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("失敗から学ぶ"))));
  }

  @Test
  void homeShowsOnlyLatestThreePublishedArticlesAndNeverDrafts() throws Exception {
    // 公開記事を古い順に4件、下書き記事を1件用意する
    for (int i = 1; i <= 4; i++) {
      var a = new Article("公開記事" + i, "home-latest-article-" + i, "概要" + i, "本文");
      a.publish();
      articles.save(a);
      Thread.sleep(5); // publishedAtの順序をテストで確実に区別するため
    }
    var draft = new Article("下書き記事タイトル", "home-latest-draft", "概要", "本文");
    articles.save(draft);

    var result =
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // 下書きは絶対に表示されない
    org.assertj.core.api.Assertions.assertThat(result).doesNotContain("下書き記事タイトル");
    // 最新3件(公開記事4・3・2)のみ表示され、最も古い公開記事1は表示されない
    org.assertj.core.api.Assertions.assertThat(result)
        .contains("公開記事4")
        .contains("公開記事3")
        .contains("公開記事2")
        .doesNotContain("公開記事1");
  }

  @Test
  void homeMoreButtonLinksToExistingArticleListPage() throws Exception {
    var a = new Article("もっと見る確認用記事", "home-more-button-check", "概要", "本文");
    a.publish();
    articles.save(a);

    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("href=\"/articles\"")));
  }
}
