package com.exradar.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/",
                        "/register",
                        "/login",
                        "/experiences",
                        "/experiences/unlock",
                        "/statistics",
                        "/statistics/**",
                        "/choices",
                        "/choices/**",
                        "/profiles/**",
                        "/css/**",
                        "/js/**",
                        "/error")
                    .permitAll()
                    .requestMatchers(new RegexRequestMatcher("^/experiences/[0-9]+$", "GET"))
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
        .logout(l -> l.logoutSuccessUrl("/?logout").permitAll());
    return http.build();
  }
}
