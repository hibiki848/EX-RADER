package com.exradar.service;

import com.exradar.dto.AdminAnalyticsDto.DailyStat;
import com.exradar.dto.AdminAnalyticsDto.DeviceShare;
import com.exradar.dto.AdminAnalyticsDto.GoogleAnalyticsStats;
import com.exradar.dto.AdminAnalyticsDto.TopPage;
import com.exradar.dto.AdminAnalyticsDto.TrafficSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Google Analytics Data API(GA4)をREST経由で呼び出す薄いクライアント。
 * Railwayの環境変数 GA4_PROPERTY_ID / GA4_SERVICE_ACCOUNT_KEY が
 * 未設定の場合はisConfigured()がfalseを返し、呼び出し側で「取得不可」として扱う。
 */
@Service
public class GoogleAnalyticsDataService {
  private static final Logger log = LoggerFactory.getLogger(GoogleAnalyticsDataService.class);
  private static final String BASE_URL = "https://analyticsdata.googleapis.com";
  private static final List<String> SCOPES =
      List.of("https://www.googleapis.com/auth/analytics.readonly");
  /** Google Analytics Data APIのbatchRunReportsは1回あたり最大5件のRunReportRequestまで。 */
  private static final int MAX_REPORTS_PER_BATCH = 5;

  private final String propertyId;
  private final GoogleCredentials credentials;
  private final RestClient restClient;

  public GoogleAnalyticsDataService(
      @Value("${exradar.analytics.ga4.property-id:}") String propertyId,
      @Value("${exradar.analytics.ga4.service-account-key:}") String serviceAccountKeyJson,
      RestClient.Builder restClientBuilder) {
    this.propertyId = propertyId;
    this.credentials = buildCredentials(serviceAccountKeyJson);
    this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
  }

  public boolean isConfigured() {
    return credentials != null && propertyId != null && !propertyId.isBlank();
  }

  private GoogleCredentials buildCredentials(String json) {
    if (json == null || json.isBlank()) return null;
    try {
      GoogleCredentials raw =
          GoogleCredentials.fromStream(
              new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
      return raw.createScoped(SCOPES);
    } catch (IOException e) {
      log.error("GA4サービスアカウントキーの読み込みに失敗しました", e);
      return null;
    }
  }

  /** Property ID・認証情報の設定有無だけをログに出す(値そのものは秘密情報のため出力しない)。 */
  private void logConfigurationState(String operation) {
    log.info("GA4 analytics request started: {}", operation);
    log.info("GA4 property configured: {}", propertyId != null && !propertyId.isBlank());
    log.info("GA4 credentials configured: {}", credentials != null);
  }

  private String accessToken() {
    try {
      credentials.refresh();
      return credentials.getAccessToken().getTokenValue();
    } catch (IOException e) {
      throw new IllegalStateException("GA4アクセストークンの取得に失敗しました", e);
    }
  }

  /** 通常レポート(サマリー・推移・人気ページ・流入元・デバイス・ファネル)をまとめて1回のAPI呼び出しで取得する。 */
  public GoogleAnalyticsStats fetchStats() {
    logConfigurationState("fetchStats");
    if (!isConfigured()) {
      log.info("GA4 analytics request skipped: not configured");
      return unavailable("GA4_PROPERTY_ID / GA4_SERVICE_ACCOUNT_KEYが設定されていません");
    }
    try {
      List<Map<String, Object>> requests =
          List.of(
              report("today", "today", List.of(), List.of("activeUsers", "screenPageViews"), null, null, null),
              report("6daysAgo", "today", List.of(), List.of("activeUsers", "screenPageViews"), null, null, null),
              report("29daysAgo", "today", List.of(), List.of("activeUsers", "newUsers"), null, null, null),
              report(
                  "29daysAgo",
                  "today",
                  List.of("date"),
                  List.of("activeUsers", "screenPageViews"),
                  Map.of("dimension", Map.of("dimensionName", "date")),
                  null,
                  null),
              report(
                  "29daysAgo",
                  "today",
                  List.of("pagePath", "pageTitle"),
                  List.of("screenPageViews"),
                  Map.of("metric", Map.of("metricName", "screenPageViews"), "desc", true),
                  10,
                  null),
              report(
                  "29daysAgo",
                  "today",
                  List.of("sessionDefaultChannelGroup"),
                  List.of("sessions"),
                  Map.of("metric", Map.of("metricName", "sessions"), "desc", true),
                  null,
                  null),
              report(
                  "29daysAgo",
                  "today",
                  List.of("deviceCategory"),
                  List.of("activeUsers"),
                  Map.of("metric", Map.of("metricName", "activeUsers"), "desc", true),
                  null,
                  null),
              report(
                  "29daysAgo",
                  "today",
                  List.of("eventName"),
                  List.of("eventCount"),
                  null,
                  null,
                  Map.of(
                      "filter",
                      Map.of(
                          "fieldName", "eventName",
                          "stringFilter", Map.of("value", "experience_detail_view")))));

      List<JsonNode> reports = runReportBatches(requests);

      long todayUsers = metricAt(reports.get(0), 0, 0);
      long todayPageViews = metricAt(reports.get(0), 0, 1);
      long last7DaysUsers = metricAt(reports.get(1), 0, 0);
      long last7DaysPageViews = metricAt(reports.get(1), 0, 1);
      long last30DaysUsers = metricAt(reports.get(2), 0, 0);
      long newUsers30d = metricAt(reports.get(2), 0, 1);
      long experienceDetailViews30d = metricAt(reports.get(7), 0, 0);

      return new GoogleAnalyticsStats(
          true,
          null,
          todayUsers,
          last7DaysUsers,
          last30DaysUsers,
          todayPageViews,
          last7DaysPageViews,
          newUsers30d,
          experienceDetailViews30d,
          dailyStats(reports.get(3)),
          topPages(reports.get(4)),
          trafficSources(reports.get(5)),
          devices(reports.get(6)),
          false,
          null);
    } catch (RuntimeException e) {
      log.error("Google Analytics Data API request failed: batchRunReports", e);
      return unavailable("Google Analyticsのデータを取得できませんでした");
    }
  }

  /**
   * Google Analytics Data APIのbatchRunReportsは1回あたり最大{@value #MAX_REPORTS_PER_BATCH}件までしか
   * RunReportRequestを受け付けないため、リクエストを分割して複数回呼び出し、結果を元の順序で結合する。
   * 取得する分析項目(リクエストの内容)自体は変更しない。
   */
  private List<JsonNode> runReportBatches(List<Map<String, Object>> requests) {
    int totalBatches =
        (int) Math.ceil(requests.size() / (double) MAX_REPORTS_PER_BATCH);
    List<JsonNode> combined = new java.util.ArrayList<>();
    int batchNumber = 0;
    for (int start = 0; start < requests.size(); start += MAX_REPORTS_PER_BATCH) {
      batchNumber++;
      List<Map<String, Object>> chunk =
          requests.subList(start, Math.min(start + MAX_REPORTS_PER_BATCH, requests.size()));
      String label =
          "batchRunReports " + batchNumber + "/" + totalBatches + " (" + chunk.size() + " requests)";
      log.info("Calling Google Analytics Data API: {}", label);
      try {
        JsonNode response =
            restClient
                .post()
                .uri("/v1beta/properties/{id}:batchRunReports", propertyId)
                .header("Authorization", "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("requests", chunk))
                .retrieve()
                .body(JsonNode.class);
        response.path("reports").forEach(combined::add);
        log.info("Google Analytics Data API request succeeded: {}", label);
      } catch (RuntimeException e) {
        log.error("Google Analytics Data API request failed: {}", label, e);
        throw e;
      }
    }
    return combined;
  }

  /** 現在のアクティブユーザー数(リアルタイムAPI)。取得できない場合はnull。 */
  public Long fetchRealtimeActiveUsers() {
    logConfigurationState("fetchRealtimeActiveUsers");
    if (!isConfigured()) {
      log.info("GA4 realtime request skipped: not configured");
      return null;
    }
    try {
      log.info("Calling Google Analytics Data API: runRealtimeReport");
      JsonNode response =
          restClient
              .post()
              .uri("/v1beta/properties/{id}:runRealtimeReport", propertyId)
              .header("Authorization", "Bearer " + accessToken())
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("metrics", List.of(Map.of("name", "activeUsers"))))
              .retrieve()
              .body(JsonNode.class);
      log.info("Google Analytics Data API request succeeded: runRealtimeReport");
      return metricAt(response, 0, 0);
    } catch (RuntimeException e) {
      log.error("Google Analytics Data API request failed: runRealtimeReport", e);
      return null;
    }
  }

  private GoogleAnalyticsStats unavailable(String message) {
    return new GoogleAnalyticsStats(
        false, message, null, null, null, null, null, null, null, null, null, null, null, false,
        null);
  }

  private Map<String, Object> report(
      String startDate,
      String endDate,
      List<String> dimensionNames,
      List<String> metricNames,
      Map<String, Object> orderBy,
      Integer limit,
      Map<String, Object> dimensionFilter) {
    Map<String, Object> req = new LinkedHashMap<>();
    req.put("dateRanges", List.of(Map.of("startDate", startDate, "endDate", endDate)));
    if (!dimensionNames.isEmpty()) {
      req.put("dimensions", dimensionNames.stream().map(n -> Map.of("name", (Object) n)).toList());
    }
    req.put("metrics", metricNames.stream().map(n -> Map.of("name", (Object) n)).toList());
    if (orderBy != null) req.put("orderBys", List.of(orderBy));
    if (limit != null) req.put("limit", String.valueOf(limit));
    if (dimensionFilter != null) req.put("dimensionFilter", dimensionFilter);
    return req;
  }

  private long metricAt(JsonNode report, int rowIndex, int metricIndex) {
    JsonNode rows = report.path("rows");
    if (!rows.isArray() || rowIndex >= rows.size()) return 0L;
    JsonNode value = rows.path(rowIndex).path("metricValues").path(metricIndex).path("value");
    return value.isMissingNode() ? 0L : Long.parseLong(value.asText("0"));
  }

  private List<DailyStat> dailyStats(JsonNode report) {
    JsonNode rows = report.path("rows");
    if (!rows.isArray()) return List.of();
    java.util.ArrayList<DailyStat> result = new java.util.ArrayList<>();
    for (JsonNode row : rows) {
      String raw = row.path("dimensionValues").path(0).path("value").asText("");
      String formatted =
          raw.length() == 8
              ? raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8)
              : raw;
      long users = Long.parseLong(row.path("metricValues").path(0).path("value").asText("0"));
      long pageViews = Long.parseLong(row.path("metricValues").path(1).path("value").asText("0"));
      result.add(new DailyStat(formatted, users, pageViews));
    }
    return result;
  }

  private List<TopPage> topPages(JsonNode report) {
    JsonNode rows = report.path("rows");
    if (!rows.isArray()) return List.of();
    java.util.ArrayList<TopPage> result = new java.util.ArrayList<>();
    for (JsonNode row : rows) {
      String path = row.path("dimensionValues").path(0).path("value").asText("");
      String title = row.path("dimensionValues").path(1).path("value").asText("");
      long views = Long.parseLong(row.path("metricValues").path(0).path("value").asText("0"));
      result.add(new TopPage(path, title, views));
    }
    return result;
  }

  private List<TrafficSource> trafficSources(JsonNode report) {
    JsonNode rows = report.path("rows");
    if (!rows.isArray()) return List.of();
    java.util.ArrayList<TrafficSource> result = new java.util.ArrayList<>();
    for (JsonNode row : rows) {
      String source = row.path("dimensionValues").path(0).path("value").asText("");
      long sessions = Long.parseLong(row.path("metricValues").path(0).path("value").asText("0"));
      result.add(new TrafficSource(source, sessions));
    }
    return result;
  }

  private List<DeviceShare> devices(JsonNode report) {
    JsonNode rows = report.path("rows");
    if (!rows.isArray()) return List.of();
    java.util.ArrayList<DeviceShare> result = new java.util.ArrayList<>();
    for (JsonNode row : rows) {
      String category = row.path("dimensionValues").path(0).path("value").asText("");
      long users = Long.parseLong(row.path("metricValues").path(0).path("value").asText("0"));
      result.add(new DeviceShare(category, users));
    }
    return result;
  }
}
