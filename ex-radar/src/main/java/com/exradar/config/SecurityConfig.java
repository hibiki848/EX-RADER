package com.exradar.config;

import com.exradar.security.CustomOidcUserService;
import com.exradar.security.OAuth2LoginFailureHandler;
import com.exradar.security.OAuth2LoginSuccessHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
public class SecurityConfig {
  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Google連携時のパスワード確認(AccountLinkController)で、既存のUserDetailsService+
   * PasswordEncoderによる標準の認証処理を再利用するために公開する。
   */
  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ObjectProvider<ClientRegistrationRepository> clientRegistrations,
      ObjectProvider<CustomOidcUserService> customOidcUserService,
      ObjectProvider<OAuth2LoginSuccessHandler> oAuth2LoginSuccessHandler,
      ObjectProvider<OAuth2LoginFailureHandler> oAuth2LoginFailureHandler)
      throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/",
                        "/register",
                        "/login",
                        "/oauth2/authorization/**",
                        "/login/oauth2/code/**",
                        "/oauth2/link-account",
                        "/experiences/unlock",
                        "/statistics",
                        "/statistics/**",
                        "/choices",
                        "/choices/**",
                        "/profiles/**",
                        "/articles",
                        "/articles/**",
                        "/robots.txt",
                        "/sitemap.xml",
                        "/css/**",
                        "/js/**",
                        "/error")
                    .permitAll()
                    // "/experiences"はGET(検索・一覧)のみ未ログインで許可する。POST(新規投稿)まで
                    // 誤って許可しないよう、他の"/experiences/**"配下と同様に認証を必須にする
                    // (以前は素のパス文字列指定によりPOSTも意図せず未ログインで到達可能だった)。
                    .requestMatchers(new RegexRequestMatcher("^/experiences$", "GET"))
                    .permitAll()
                    .requestMatchers(new RegexRequestMatcher("^/experiences/[0-9]+$", "GET"))
                    .permitAll()
                    .requestMatchers("/admin/**", "/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
        .logout(l -> l.logoutSuccessUrl("/?logout").permitAll())
        // /admin/**等でのロール不足やCSRF不備によるアクセス拒否を、応答内容は変えずに
        // WARNログへ記録する(不正アクセス・権限不足の追跡用)。実際の拒否・エラーページ表示は
        // 従来どおりAccessDeniedHandlerImplに委譲する。
        .exceptionHandling(
            e ->
                e.accessDeniedHandler(
                    (request, response, ex) -> {
                      log.warn(
                          "Access denied by security filter chain: method={} path={} userId={}",
                          request.getMethod(),
                          request.getRequestURI(),
                          request.getUserPrincipal() != null
                              ? request.getUserPrincipal().getName()
                              : "anonymous");
                      new AccessDeniedHandlerImpl().handle(request, response, ex);
                    }));

    // GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRETが設定されている場合のみGoogleログインを有効化する。
    // 未設定の開発・テスト環境では既存の認証(フォームログインのみ)を一切変更しない。
    if (clientRegistrations.getIfAvailable() != null) {
      http.oauth2Login(
          oauth2 ->
              oauth2
                  .loginPage("/login")
                  .userInfoEndpoint(u -> u.oidcUserService(customOidcUserService.getObject()))
                  .successHandler(oAuth2LoginSuccessHandler.getObject())
                  .failureHandler(oAuth2LoginFailureHandler.getObject()));
    }
    return http.build();
  }
}
