package com.exradar.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;

/**
 * SecurityConfigの/experiences系はRegexRequestMatcherでpermitAllを判定している。
 * RegexRequestMatcherはデフォルトでクエリ文字列込みのURLに対して正規表現をマッチさせるため、
 * 末尾に(\?.*)?を付けていないと「クエリ文字列が付いた瞬間にどのGETも一致しなくなり、
 * 未ログインでも見えるはずの検索・一覧・詳細ページがことごとくログインへリダイレクトされる」
 * という不具合が起きる。この不具合はMockMvc(このプロジェクトの他のテストが使っている方式)
 * ではクエリ文字列の扱いが異なるため再現せず、実際のサーブレットコンテナでしか顕在化しない。
 * そのため、ここだけは実際に組み込みTomcatを起動する@SpringBootTest(RANDOM_PORT)で検証する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExperiencesQueryStringSecurityTest {
  @LocalServerPort int port;
  @Autowired TestRestTemplate rest;

  @Test
  void anonymousSearchWithQueryStringIsNotRedirectedToLogin() {
    var response =
        rest.getForEntity(url("/experiences?keyword=test&page=0&sort=latest"), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void anonymousJsonListApiWithQueryStringIsNotRedirectedToLogin() {
    var request =
        RequestEntity.get(URI.create(url("/experiences?page=0")))
            .accept(MediaType.APPLICATION_JSON)
            .build();
    var response = rest.exchange(request, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
  }

  @Test
  void anonymousDetailPageWithQueryStringIsNotRedirectedToLogin() {
    // 存在しないIDでもよい(見つからなければ404になるだけで、ここで確認したいのは
    // クエリ文字列付きでもpermitAllのURLパターンに一致し、302でログインへ飛ばされないこと)。
    var response = rest.getForEntity(url("/experiences/999999999?utm_source=test"), String.class);
    assertThat(response.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.OK);
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
