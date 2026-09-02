package com.exradar.service;

import com.exradar.dto.AdminAnalyticsDto;
import com.exradar.dto.AdminAnalyticsDto.DatabaseStats;
import com.exradar.dto.AdminAnalyticsDto.GoogleAnalyticsStats;
import com.exradar.dto.AdminAnalyticsDto.UsageFunnel;
import com.exradar.entity.Role;
import com.exradar.repository.CommentRepository;
import com.exradar.repository.DecisionMemoRepository;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.repository.ReactionRepository;
import com.exradar.repository.ReportRepository;
import com.exradar.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 管理者ダッシュボードの運営状況(GA4 + EXレーダーDB)を1つにまとめるサービス。
 * GA4 APIは短時間キャッシュして、管理者画面を開くたびに大量のリクエストが
 * 発生しないようにする(現状の規模ではこれで十分なため、Redis等は使わない)。
 */
@Service
public class AdminAnalyticsService {
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);

  private final UserRepository users;
  private final ExperiencePostRepository posts;
  private final CommentRepository comments;
  private final ReactionRepository reactions;
  private final DecisionMemoRepository decisionMemos;
  private final ReportRepository reports;
  private final GoogleAnalyticsDataService googleAnalytics;

  private volatile CachedResult cache;

  public AdminAnalyticsService(
      UserRepository users,
      ExperiencePostRepository posts,
      CommentRepository comments,
      ReactionRepository reactions,
      DecisionMemoRepository decisionMemos,
      ReportRepository reports,
      GoogleAnalyticsDataService googleAnalytics) {
    this.users = users;
    this.posts = posts;
    this.comments = comments;
    this.reactions = reactions;
    this.decisionMemos = decisionMemos;
    this.reports = reports;
    this.googleAnalytics = googleAnalytics;
  }

  public AdminAnalyticsDto dashboard() {
    CachedResult cached = cache;
    if (cached != null && Duration.between(cached.fetchedAt, Instant.now()).compareTo(CACHE_TTL) < 0) {
      return cached.dto;
    }
    AdminAnalyticsDto dto = build();
    cache = new CachedResult(Instant.now(), dto);
    return dto;
  }

  private AdminAnalyticsDto build() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime since7d = now.minusDays(7);
    LocalDateTime since30d = now.minusDays(30);

    // 管理者・analyticsExcluded=trueのアカウント(運営者の動作確認用など)は
    // 「実際の利用者」ではないため、アクセス解析上のユーザー数には含めない。
    DatabaseStats db =
        new DatabaseStats(
            users.countAnalyticsEligible(Role.ADMIN),
            users.countAnalyticsEligibleSince(Role.ADMIN, since7d),
            users.countAnalyticsEligibleSince(Role.ADMIN, since30d),
            posts.count(),
            posts.countByCreatedAtAfter(since7d),
            comments.count(),
            reactions.count(),
            decisionMemos.count(),
            reports.count());

    GoogleAnalyticsStats ga = fetchGoogleAnalyticsStats();

    UsageFunnel funnel =
        new UsageFunnel(
            ga.available() ? ga.last30DaysUsers() : null,
            ga.available() ? ga.experienceDetailViews30d() : null,
            db.newUsers30d(),
            posts.countDistinctAuthorsSince(since30d),
            posts.countByCreatedAtAfter(since30d));

    return new AdminAnalyticsDto(Instant.now(), db, ga, funnel);
  }

  private GoogleAnalyticsStats fetchGoogleAnalyticsStats() {
    GoogleAnalyticsStats stats = googleAnalytics.fetchStats();
    if (!stats.available()) return stats;
    Long realtime = googleAnalytics.fetchRealtimeActiveUsers();
    return new GoogleAnalyticsStats(
        stats.available(),
        stats.errorMessage(),
        stats.todayUsers(),
        stats.last7DaysUsers(),
        stats.last30DaysUsers(),
        stats.todayPageViews(),
        stats.last7DaysPageViews(),
        stats.newUsers30d(),
        stats.experienceDetailViews30d(),
        stats.dailyStats(),
        stats.topPages(),
        stats.trafficSources(),
        stats.devices(),
        realtime != null,
        realtime);
  }

  private record CachedResult(Instant fetchedAt, AdminAnalyticsDto dto) {}
}
