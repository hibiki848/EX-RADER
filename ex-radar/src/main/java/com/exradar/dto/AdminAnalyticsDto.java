package com.exradar.dto;

import java.time.Instant;
import java.util.List;

/** 管理者ダッシュボードの運営状況(GA4 + DB内部データ)をまとめたレスポンス。 */
public record AdminAnalyticsDto(
    Instant generatedAt,
    DatabaseStats database,
    GoogleAnalyticsStats googleAnalytics,
    UsageFunnel funnel) {

  /** EXレーダーDBから取得した、常に取得可能な内部指標。 */
  public record DatabaseStats(
      long totalUsers,
      long newUsers7d,
      long newUsers30d,
      long totalPosts,
      long posts7d,
      long commentCount,
      long reactionCount,
      long decisionMemoCount,
      long reportCount) {}

  /**
   * GA4(Google Analytics Data API)から取得した指標。
   * available=false のときは各数値は取得できていない(0件と区別するためnull)。
   * realtimeAvailable はリアルタイムAPIの成否を別管理する(通常レポートが取れても
   * リアルタイムAPIだけ失敗する場合があるため)。
   */
  public record GoogleAnalyticsStats(
      boolean available,
      String errorMessage,
      Long todayUsers,
      Long last7DaysUsers,
      Long last30DaysUsers,
      Long todayPageViews,
      Long last7DaysPageViews,
      Long newUsers30d,
      Long experienceDetailViews30d,
      List<DailyStat> dailyStats,
      List<TopPage> topPages,
      List<TrafficSource> trafficSources,
      List<DeviceShare> devices,
      boolean realtimeAvailable,
      Long realtimeActiveUsers) {}

  public record DailyStat(String date, long users, long pageViews) {}

  public record TopPage(String path, String title, long pageViews) {}

  public record TrafficSource(String source, long sessions) {}

  public record DeviceShare(String category, long activeUsers) {}

  /**
   * 「サイト訪問 → 体験談を見る → ユーザー登録 → 体験談を投稿する」の利用ファネル(過去30日間)。
   * visitors30d・experienceDetailViews30dはGA4由来(取得できない場合はnull)。
   * newRegistrations30d・posters30d・posts30dはEXレーダーDB由来で常に正確な値。
   * GA4側に会員登録・投稿完了を示すイベント(sign_up等)は設定されていないため、
   * それらの指標は推測せずDBの実データのみを使用している。
   */
  public record UsageFunnel(
      Long visitors30d,
      Long experienceDetailViews30d,
      long newRegistrations30d,
      long posters30d,
      long posts30d) {}
}
