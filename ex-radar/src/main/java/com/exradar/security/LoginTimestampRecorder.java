package com.exradar.security;

import com.exradar.repository.UserRepository;
import java.time.LocalDateTime;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ログイン成功時(ローカルのフォームログイン・Googleログインの両方)に
 * User#firstLoginAt/lastLoginAtを記録する唯一の場所。
 *
 * Spring SecurityはAuthenticationSuccessEventを認証方式に関わらず一律で発行するため、
 * ここ1箇所だけをフックすればよい(SecurityConfigのformLogin()にはローカル用の
 * successHandlerが存在せず、Google側はOAuth2LoginSuccessHandlerが別に持つリダイレクト
 * ロジックを担っている — ログイン記録をそちらへ混ぜず、認証方式に依存しないこの
 * ApplicationListenerへ一元化することで、ロジックの重複を避けている)。
 *
 * ローカルログイン: UsernamePasswordAuthenticationTokenのgetName()はUserDetails#getUsername()
 * (=メールアドレス、ExRadarUserDetailsService参照)を返す。
 * Googleログイン: CustomOidcUserServiceがDefaultOidcUserのnameAttributeKeyを"email"に
 * 指定して生成しているため、getName()は同様にメールアドレスを返す。
 * どちらの経路でもauthentication.getName()がメールアドレスになるため、分岐は不要。
 */
@Component
public class LoginTimestampRecorder implements ApplicationListener<AuthenticationSuccessEvent> {
  private final UserRepository users;

  public LoginTimestampRecorder(UserRepository users) {
    this.users = users;
  }

  @Override
  @Transactional
  public void onApplicationEvent(AuthenticationSuccessEvent event) {
    String email = event.getAuthentication().getName();
    if (email == null || email.isBlank()) return;
    users.findByEmailIgnoreCase(email).ifPresent(u -> u.recordLogin(LocalDateTime.now()));
  }
}
