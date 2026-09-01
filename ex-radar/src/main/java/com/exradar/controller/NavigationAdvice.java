package com.exradar.controller;

import com.exradar.service.AccountService;
import java.security.Principal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class NavigationAdvice {
  private final ObjectProvider<AccountService> service;

  @Value("${exradar.analytics.measurement-id:}")
  private String gaMeasurementId;

  public NavigationAdvice(ObjectProvider<AccountService> s) {
    service = s;
  }

  @ModelAttribute("unreadNotificationCount")
  public long unread(Principal p) {
    var a = service.getIfAvailable();
    return p == null || a == null ? 0 : a.unread(p.getName());
  }

  @ModelAttribute("gaMeasurementId")
  public String gaMeasurementId() {
    return gaMeasurementId;
  }
}
