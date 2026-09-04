package com.exradar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

import com.exradar.security.CustomOidcUserService;
import com.exradar.security.OAuth2LoginFailureHandler;
import com.exradar.security.OAuth2LoginSuccessHandler;

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
                        "/images/**",
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
                        "/terms",
                        "/privacy",
                        "/contact",
                        "/guidelines",
                        "/robots.txt",
                        "/sitemap.xml",
                        "/css/**",
                        "/js/**",
                        "/error",
                        "/webhooks/stripe")
                    .permitAll()
                    // "/experiences"はGET(検索・一覧)のみ未ログインで許可する。POST(新規投稿)まで
                    // 誤って許可しないよう、他の"/experiences/**"配下と同様に認証を必須にする
                    // (以前は素のパス文字列指定によりPOSTも意図せず未ログインで到達可能だった)。
                    // 末尾の(\?.*)?は必須: RegexRequestMatcherはクエリ文字列込みのURLに対して
                    // 正規表現をマッチさせるため、これが無いと検索条件付き(?keyword=...等)の
                    // GETがどれも一致せず、未ログイン検索・一覧のページングが軒並みログインへ
                    // リダイレクトされてしまう(実サーバーでのみ再現し、MockMvcのテストでは
                    // クエリ文字列の扱いが異なるため気づけない)。
                    .requestMatchers(new RegexRequestMatcher("^/experiences(\\?.*)?$", "GET"))
                    .permitAll()
                    .requestMatchers(new RegexRequestMatcher("^/experiences/[0-9]+(\\?.*)?$", "GET"))
                    .permitAll()
                    // 体験談詳細の簡易表示(要点)・表示方法選択も、詳細ページ本体と同じ公開範囲にする
                    // (同じ内容の別表示形式に過ぎないため、詳細ページだけ公開して片方だけ
                    // ログイン必須のままにすると閲覧制限として一貫しない)。GET以外は含めない。
                    .requestMatchers(
                        new RegexRequestMatcher(
                            "^/experiences/[0-9]+/(view-options|summary)(\\?.*)?$", "GET"))
                    .permitAll()
                    .requestMatchers("/admin/**", "/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        // Stripe Webhookは外部(Stripe)からのサーバー間POSTのため、ブラウザセッションに
        // 紐づくCSRFトークンを持たない。署名検証(Stripe-Signatureヘッダー)自体が
        // このエンドポイントの正当性確認を代替するため、ここだけCSRF検証の対象から外す。
        .csrf(csrf -> csrf.ignoringRequestMatchers("/webhooks/stripe"))
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
