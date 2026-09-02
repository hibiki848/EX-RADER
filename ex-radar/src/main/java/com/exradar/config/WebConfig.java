package com.exradar.config;

import com.exradar.security.DisplayNameSetupInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final DisplayNameSetupInterceptor displayNameSetupInterceptor;

  public WebConfig(DisplayNameSetupInterceptor displayNameSetupInterceptor) {
    this.displayNameSetupInterceptor = displayNameSetupInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(displayNameSetupInterceptor)
        .excludePathPatterns(
            "/oauth2/display-name",
            "/login",
            "/login/**",
            "/oauth2/authorization/**",
            "/logout",
            "/css/**",
            "/js/**",
            "/error",
            "/robots.txt",
            "/sitemap.xml");
  }
}
