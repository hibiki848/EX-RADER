package com.exradar.controller;

import com.exradar.dto.AdminAnalyticsDto;
import com.exradar.service.AdminAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理者ダッシュボード用のGA4+DB統計API。アクセス制御はSecurityConfigの/api/admin/**でADMINロールに限定している。 */
@RestController
@RequestMapping("/api/admin")
public class AdminAnalyticsController {
  private final AdminAnalyticsService service;

  public AdminAnalyticsController(AdminAnalyticsService service) {
    this.service = service;
  }

  @GetMapping("/analytics")
  public AdminAnalyticsDto analytics() {
    return service.dashboard();
  }
}
