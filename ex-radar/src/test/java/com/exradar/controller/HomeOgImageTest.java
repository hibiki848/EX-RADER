package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OG_DEFAULT_IMAGE_URL(exradar.seo.default-og-image)が設定されている本番相当の状態で、
 * トップページのog:imageが維持されていることを確認する。デフォルトのテスト設定では
 * この値が空のため、og:imageタグ自体が出ない(fragments/seo.htmlの仕様どおり)。
 * そのため専用のプロパティを指定した別のApplicationContextで検証する。
 */
@SpringBootTest(properties = "exradar.seo.default-og-image=/images/og/exradar-ogp.png")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeOgImageTest {
  @Autowired MockMvc mvc;

  @Test
  void homeKeepsExistingOgImageWhenConfigured() throws Exception {
    var body = mvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(body).contains("property=\"og:image\"", "/images/og/exradar-ogp.png");
  }
}
