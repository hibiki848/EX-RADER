package com.exradar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
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
  private static final Logger log = LoggerFactory.getLogger(GoogleOAuth2Config.class);

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

  /**
   * 起動時にGoogleログインが有効化されたかどうかを必ずログへ出す。
   * Railwayのデプロイログでこの1行を見れば、GOOGLE_CLIENT_ID等の環境変数が
   * 実際に読み込まれているかどうかを推測せず確認できる(値そのものは出力しない)。
   */
  @EventListener(ApplicationReadyEvent.class)
  void logGoogleLoginStatus(ApplicationReadyEvent event) {
    ObjectProvider<ClientRegistrationRepository> provider =
        event.getApplicationContext().getBeanProvider(ClientRegistrationRepository.class);
    if (provider.getIfAvailable() != null) {
      log.info("Google login: ENABLED (GOOGLE_CLIENT_ID is set)");
    } else {
      log.info("Google login: DISABLED (GOOGLE_CLIENT_ID is not set) — Googleボタンは表示されません");
    }
  }
}
