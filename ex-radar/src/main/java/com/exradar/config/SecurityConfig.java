package com.exradar.config;

import com.exradar.security.CustomOidcUserService;
import com.exradar.security.OAuth2LoginFailureHandler;
import com.exradar.security.OAuth2LoginSuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
public class SecurityConfig {
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
                        "/experiences",
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
                    .requestMatchers(new RegexRequestMatcher("^/experiences/[0-9]+$", "GET"))
                    .permitAll()
                    .requestMatchers("/admin/**", "/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
        .logout(l -> l.logoutSuccessUrl("/?logout").permitAll());

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
