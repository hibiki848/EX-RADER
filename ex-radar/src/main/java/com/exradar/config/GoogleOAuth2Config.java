package com.exradar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * GoogleログインをGOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET環境変数が設定されている場合だけ有効化する。
 * これらが未設定の環境(現状の開発・テスト環境)ではClientRegistrationRepositoryビーンを作成しないため、
 * SecurityConfig側でoauth2Loginの組み込みがスキップされ、既存のフォームログインのみの挙動が維持される。
 * 値はapplication.yml経由ではなく環境変数から直接読み込むため、GitHubへ秘密鍵がコミットされることはない。
 */
@Configuration
public class GoogleOAuth2Config {
  @Bean
  @ConditionalOnProperty(name = "GOOGLE_CLIENT_ID")
  ClientRegistrationRepository clientRegistrationRepository(
      @Value("${GOOGLE_CLIENT_ID}") String clientId,
      @Value("${GOOGLE_CLIENT_SECRET}") String clientSecret) {
    ClientRegistration google =
        CommonOAuth2Provider.GOOGLE
            .getBuilder("google")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build();
    return new InMemoryClientRegistrationRepository(google);
  }
}
