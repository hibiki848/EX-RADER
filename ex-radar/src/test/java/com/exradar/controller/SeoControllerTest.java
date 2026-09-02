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
 * SEO_INDEXABLE(exradar.seo.indexable)が未設定/true(=本番の既定仕様)の場合の検証。
 * falseの場合(staging等)の検証はSeoIndexingDisabledTestで行う
 * (プロパティが異なるため別のApplicationContextになる)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeoControllerTest {
  @Autowired MockMvc mvc;

  @Test
  void robotsTxtKeepsExistingProductionRulesAndSitemapLineByDefault() throws Exception {
    var body =
        mvc.perform(get("/robots.txt"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).contains("Disallow: /admin", "Disallow: /experiences/unlock", "Sitemap: ");
    // 全面禁止(User-agent: * の直後にDisallow: /だけ)にはなっていないこと
    assertThat(body).doesNotContain("Disallow: /\n");
  }

  @Test
  void sitemapXmlIsServedNormallyByDefault() throws Exception {
    mvc.perform(get("/sitemap.xml"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("<urlset")));
  }

  @Test
  void htmlResponsesHaveNoXRobotsTagHeaderByDefault() throws Exception {
    mvc.perform(get("/")).andExpect(status().isOk()).andExpect(header().doesNotExist("X-Robots-Tag"));
  }

  @Test
  void metaRobotsNoindexIsNotRenderedByDefault() throws Exception {
    var body = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(body).doesNotContain("noindex,nofollow,noarchive");
  }
}
