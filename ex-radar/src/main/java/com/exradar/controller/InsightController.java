package com.exradar.controller;

import com.exradar.service.InsightService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class InsightController {
  private final InsightService insights;

  public InsightController(InsightService insights) {
    this.insights = insights;
  }

  @GetMapping("/statistics")
  String statistics(Model m) {
    m.addAttribute("statistics", insights.publicStatistics());
    return "insights/statistics";
  }

  @GetMapping("/insights")
  String dashboard(Principal p, Model m) {
    m.addAttribute("insight", insights.dashboard(p.getName()));
    return "insights/dashboard";
  }

  @GetMapping("/insights/detailed")
  String detailed(Principal p, Model m) {
    m.addAttribute("statistics", insights.detailedStatistics(p.getName()));
    m.addAttribute("detailMode", "detailed");
    return "insights/detail";
  }

  @GetMapping("/insights/next-routes")
  String routes(Principal p, Model m) {
    m.addAttribute("routes", insights.nextRoutes(p.getName()));
    m.addAttribute("detailMode", "routes");
    return "insights/detail";
  }

  @GetMapping("/insights/trends")
  String trends(Principal p, Model m) {
    m.addAttribute("statistics", insights.satisfactionTrends(p.getName()));
    m.addAttribute("detailMode", "trends");
    return "insights/detail";
  }

  @GetMapping("/life-report")
  String report(Principal p, Model m) {
    m.addAttribute("report", insights.lifeReport(p.getName()));
    return "insights/life-report";
  }
}
