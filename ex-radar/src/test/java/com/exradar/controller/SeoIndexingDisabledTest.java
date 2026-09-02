package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * exradar.seo.indexable=false(環境変数SEO_INDEXABLE=false相当。staging等の
 * 非公開検索環境を想定)の場合の挙動を検証する。hostname等ではなく、この
 * プロパティだけで切り替わることを@SpringBootTest(properties=...)で確認する。
 */
@SpringBootTest(properties = "exradar.seo.indexable=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeoIndexingDisabledTest {
  @Autowired MockMvc mvc;

  @Test
  void robotsTxtDisallowsEverythingAndOmitsSitemapLine() throws Exception {
    mvc.perform(get("/robots.txt"))
        .andExpect(status().isOk())
        .andExpect(content().string("User-agent: *\nDisallow: /\n"));
  }

  @Test
  void everyHtmlResponseCarriesXRobotsTagNoindex() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"));
  }

  /** robots.txtの内容自体だけでなく、そのレスポンス自体にもX-Robots-Tagが付くことを確認する。 */
  @Test
  void xRobotsTagAlsoAppliesToRobotsTxtAndSitemapXmlResponses() throws Exception {
    mvc.perform(get("/robots.txt"))
        .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"));
    mvc.perform(get("/sitemap.xml"))
        .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"));
  }

  @Test
  void metaRobotsNoindexIsRenderedInPageHead() throws Exception {
    var body = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(body).contains("noindex,nofollow,noarchive");
  }

  /** インデックス制御以外の通常機能(ページ本文の表示)は引き続き使えること。 */
  @Test
  void normalPageContentIsStillServedNormally() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("EXレーダー")));
  }
}
