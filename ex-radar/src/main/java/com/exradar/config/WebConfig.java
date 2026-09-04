package com.exradar.config;

import com.exradar.security.DisplayNameSetupInterceptor;
import com.exradar.security.TermsConsentInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final DisplayNameSetupInterceptor displayNameSetupInterceptor;
  private final TermsConsentInterceptor termsConsentInterceptor;

  public WebConfig(
      DisplayNameSetupInterceptor displayNameSetupInterceptor,
      TermsConsentInterceptor termsConsentInterceptor) {
    this.displayNameSetupInterceptor = displayNameSetupInterceptor;
    this.termsConsentInterceptor = termsConsentInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // termsConsentInterceptorを先に登録する: 新規Googleユーザーは規約同意が
    // 表示名設定より優先されるべきため(同意未完了のまま表示名設定画面だけ終えて
    // 同意をすり抜けられないようにする)。
    registry
        .addInterceptor(termsConsentInterceptor)
        .excludePathPatterns(
            "/auth/consent",
            "/terms",
            "/privacy",
            "/contact",
            "/guidelines",
            "/login",
            "/login/**",
            "/oauth2/authorization/**",
            "/logout",
            "/css/**",
            "/js/**",
            "/images/**",
            "/error",
            "/robots.txt",
            "/sitemap.xml");
    registry
        .addInterceptor(displayNameSetupInterceptor)
        .excludePathPatterns(
            "/oauth2/display-name",
            // 規約同意が先に必要な新規Googleユーザー(termsConsentPending=true)は
            // displayNamePendingも同時にtrueのため、これを除外しないと/auth/consentへの
            // アクセス自体がこちらのインターセプターに奪われ、表示名設定画面へ
            // 差し戻されてしまい同意画面へたどり着けなくなる。
            "/auth/consent",
            "/terms",
            "/privacy",
            "/contact",
            "/guidelines",
            "/login",
            "/login/**",
            "/oauth2/authorization/**",
            "/logout",
            "/css/**",
            "/js/**",
            "/images/**",
            "/error",
            "/robots.txt",
            "/sitemap.xml");
  }
}
