package com.exradar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * {@link LegacyDomainRedirectFilter}をFilterChainへ登録する。
 *
 * <p>server.forward-headers-strategy=frameworkにより、SpringBootは{@code ForwardedHeaderFilter}を
 * {@code Ordered.HIGHEST_PRECEDENCE}で自動登録し、X-Forwarded-*ヘッダーを反映した
 * HttpServletRequestへ差し替える。このリダイレクトフィルターはその直後・かつSpring Securityの
 * フィルターチェーン(FilterChainProxy、既定でSecurityProperties.DEFAULT_FILTER_ORDERというかなり
 * 後方の優先度)より前に置くことで、以下を両立させる。
 * <ul>
 *   <li>X-Forwarded-Host反映後の正しいホスト名で判定できる</li>
 *   <li>旧ドメイン宛のリクエストは認証・セッション処理(Spring Security)に一切入らず、
 *       ここで301を返して完結する(通常のSpring Security処理へ影響を与えない)</li>
 * </ul>
 */
@Configuration
public class LegacyDomainRedirectConfig {

  @Bean
  FilterRegistrationBean<LegacyDomainRedirectFilter> legacyDomainRedirectFilter(
      @Value("${exradar.legacy-host:}") String legacyHost,
      @Value("${exradar.canonical-host:ex-radar.jp}") String canonicalHost) {
    FilterRegistrationBean<LegacyDomainRedirectFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new LegacyDomainRedirectFilter(legacyHost, canonicalHost));
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    registration.setName("legacyDomainRedirectFilter");
    return registration;
  }
}
