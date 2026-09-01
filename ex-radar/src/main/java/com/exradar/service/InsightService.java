package com.exradar.service;

import com.exradar.dto.*;
import com.exradar.entity.*;
import com.exradar.exception.ForbiddenOperationException;
import com.exradar.repository.*;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InsightService {
  private static final String OTHER = "未設定";
  private final ExperiencePostRepository posts;
  private final UserRepository users;

  public InsightService(ExperiencePostRepository posts, UserRepository users) {
    this.posts = posts;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public List<CategoryStatisticsDto> publicStatistics() {
    return insightPosts().stream()
        .collect(
            Collectors.groupingBy(
                p -> p.getCategory().getId(), LinkedHashMap::new, Collectors.toList()))
        .values()
        .stream()
        .map(this::statistics)
        .sorted(Comparator.comparing(CategoryStatisticsDto::categoryName))
        .toList();
  }

  @Transactional(readOnly = true)
  public CategoryStatisticsDto categoryStatistics(Long categoryId) {
    if (categoryId == null) return emptyStatistics("すべて");
    var selected =
        insightPosts().stream().filter(p -> p.getCategory().getId().equals(categoryId)).toList();
    return selected.isEmpty() ? emptyStatistics("該当カテゴリ") : statistics(selected);
  }

  @Transactional(readOnly = true)
  public GiveToGetDto dashboard(String email) {
    User me = requireUser(email);
    var mine = posts.findByAuthorIdAndPublishedTrue(me.getId());
    int count = mine.size();
    int level = Math.min(count, 5);
    var all = insightPosts();
    var basic = all.isEmpty() ? emptyStatistics("すべて") : statistics(all, true);
    var unlocked = new ArrayList<String>();
    var locked = new ArrayList<String>();
    String[] stages = {"基本統計", "似た状況の人の教訓", "知っておきたかったこと", "経験後の進路（参考）", "経験者が感じた傾向"};
    for (int i = 0; i < stages.length; i++) (level > i ? unlocked : locked).add(stages[i]);
    var similar = level >= 2 ? similarUsers(me, mine, all) : List.<SimilarityDto>of();
    var similarPosts = level >= 2 ? similarPosts(me, mine, all) : List.<SimilarityDto>of();
    return new GiveToGetDto(
        count, level, List.copyOf(unlocked), List.copyOf(locked), basic, similarPosts, similar);
  }

  @Transactional(readOnly = true)
  public CategoryStatisticsDto detailedStatistics(String email) {
    requireContribution(email, 3);
    return dashboard(email).basicStatistics();
  }

  @Transactional(readOnly = true)
  public List<CategoryStatisticsDto.LabelCount> nextRoutes(String email) {
    requireContribution(email, 4);
    return dashboard(email).basicStatistics().nextRoutes();
  }

  @Transactional(readOnly = true)
  public CategoryStatisticsDto satisfactionTrends(String email) {
    requireContribution(email, 5);
    return dashboard(email).basicStatistics();
  }

  @Transactional(readOnly = true)
  public LifeReportDto lifeReport(String email) {
    User me = requireUser(email);
    var mine = posts.findByAuthorIdAndPublishedTrue(me.getId());
    var all = insightPosts();
    var categories = top(mine.stream().map(p -> p.getCategory().getName()).toList(), 8);
    var route =
        mine.stream()
            .sorted(Comparator.comparing(ExperiencePost::getCreatedAt))
            .flatMap(
                p -> {
                  if (p.getLifeEvents().isEmpty())
                    return java.util.stream.Stream.of(
                        new LifeReportDto.RouteItem(
                            p.getChoiceMade(), p.getCategory().getName(), p.getOutcome(), "現在"));
                  return p.getLifeEvents().stream()
                      .map(
                          e ->
                              new LifeReportDto.RouteItem(
                                  p.getChoiceMade(),
                                  p.getCategory().getName(),
                                  e.getTitle(),
                                  value(e.getAgeLabel())));
                })
            .toList();
    var similarPosts =
        mine.stream()
            .flatMap(m -> all.stream())
            .filter(p -> !p.getAuthor().getId().equals(me.getId()))
            .filter(p -> mine.stream().anyMatch(m -> postScore(m, p) >= 50))
            .map(ExperiencePost::getId)
            .distinct()
            .count();
    var similarUserCount = mine.isEmpty() ? 0 : similarUsers(me, mine, all).size();
    return new LifeReportDto(
        mine.size(),
        average(mine, ExperiencePost::getSatisfaction),
        average(mine, ExperiencePost::getRegret),
        percentage(mine.stream().filter(ExperiencePost::isChooseAgain).count(), mine.size()),
        categories,
        route,
        all.size(),
        similarPosts,
        similarUserCount);
  }

  public int postScore(ExperiencePost a, ExperiencePost b) {
    int score = 0;
    if (a.getCategory().getId().equals(b.getCategory().getId())) score += 40;
    if (Objects.equals(normal(a.getCurrentAgeGroup()), normal(b.getCurrentAgeGroup()))) score += 15;
    if (Objects.equals(normal(a.getStatusAtChoice()), normal(b.getStatusAtChoice()))) score += 15;
    var at = a.getTags().stream().map(t -> normal(t.getName())).collect(Collectors.toSet());
    var bt = b.getTags().stream().map(t -> normal(t.getName())).collect(Collectors.toSet());
    if (!at.isEmpty()) {
      var common = new HashSet<>(at);
      common.retainAll(bt);
      score += Math.round(20f * common.size() / at.size());
    }
    int evaluationDistance =
        Math.abs(a.getSatisfaction() - b.getSatisfaction())
            + Math.abs(a.getRegret() - b.getRegret());
    score += Math.max(0, 10 - evaluationDistance / 2);
    return Math.min(100, score);
  }

  private List<SimilarityDto> similarUsers(
      User me, List<ExperiencePost> mine, List<ExperiencePost> all) {
    return all.stream()
        .filter(p -> !p.getAuthor().getId().equals(me.getId()))
        .collect(Collectors.groupingBy(ExperiencePost::getAuthor))
        .entrySet()
        .stream()
        .map(
            e -> {
              int score =
                  (int)
                      Math.round(
                          e.getValue().stream()
                              .mapToInt(
                                  p -> mine.stream().mapToInt(m -> postScore(m, p)).max().orElse(0))
                              .average()
                              .orElse(0));
              return new SimilarityDto(
                  e.getKey().getId(),
                  "",
                  e.getKey().getDisplayName(),
                  score,
                  "カテゴリ40・年代15・当時の立場15・タグ20・評価傾向10",
                  true);
            })
        .filter(s -> s.score() > 0)
        .sorted(Comparator.comparingInt(SimilarityDto::score).reversed())
        .limit(6)
        .toList();
  }

  private List<SimilarityDto> similarPosts(
      User me, List<ExperiencePost> mine, List<ExperiencePost> all) {
    return all.stream()
        .filter(p -> !p.getAuthor().getId().equals(me.getId()))
        .map(
            p -> {
              int score = mine.stream().mapToInt(m -> postScore(m, p)).max().orElse(0);
              return new SimilarityDto(
                  p.getId(),
                  p.getTitle(),
                  p.getAuthor().getDisplayName(),
                  score,
                  "カテゴリ40・年代15・当時の立場15・タグ20・評価傾向10",
                  false);
            })
        .filter(s -> s.score() > 0)
        .sorted(Comparator.comparingInt(SimilarityDto::score).reversed())
        .limit(6)
        .toList();
  }

  private void requireContribution(String email, int requiredCount) {
    var me = requireUser(email);
    if (posts.findByAuthorIdAndPublishedTrue(me.getId()).size() < requiredCount)
      throw new ForbiddenOperationException(
          "この分析は公開した体験談が" + requiredCount + "件になると利用できます");
  }

  private User requireUser(String email) {
    if (email == null || email.isBlank()) throw new ForbiddenOperationException("ログインが必要です");
    return users
        .findByEmailIgnoreCase(email)
        .orElseThrow(() -> new ForbiddenOperationException("ユーザーを確認できません"));
  }

  private List<ExperiencePost> insightPosts() {
    return posts.findTop500ByPublishedTrueOrderByCreatedAtDesc().stream()
        .filter(p -> !p.getAuthor().isSuspended())
        .toList();
  }

  private CategoryStatisticsDto statistics(List<ExperiencePost> values) {
    return statistics(values, false);
  }

  private CategoryStatisticsDto statistics(List<ExperiencePost> values, boolean combined) {
    var first = values.getFirst();
    return new CategoryStatisticsDto(
        combined ? null : first.getCategory().getId(),
        combined ? "すべて" : first.getCategory().getName(),
        values.size(),
        values.stream().map(p -> p.getAuthor().getId()).distinct().count(),
        average(values, ExperiencePost::getSatisfaction),
        average(values, ExperiencePost::getRegret),
        percentage(values.stream().filter(ExperiencePost::isChooseAgain).count(), values.size()),
        top(values.stream().map(ExperiencePost::getDifficulties).toList(), 5),
        top(
            values.stream()
                .flatMap(p -> p.getLifeEvents().stream())
                .map(LifeEvent::getTitle)
                .toList(),
            5),
        top(values.stream().map(p -> value(p.getAuthor().getOccupation())).toList(), 5));
  }

  private CategoryStatisticsDto emptyStatistics(String name) {
    return new CategoryStatisticsDto(null, name, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
  }

  private static double average(
      List<ExperiencePost> values, java.util.function.ToIntFunction<ExperiencePost> fn) {
    return round(values.stream().mapToInt(fn).average().orElse(0));
  }

  private static double percentage(long n, long total) {
    return total == 0 ? 0 : round(n * 100d / total);
  }

  private static double round(double n) {
    return java.math.BigDecimal.valueOf(n).setScale(1, RoundingMode.HALF_UP).doubleValue();
  }

  private static List<CategoryStatisticsDto.LabelCount> top(List<String> source, int limit) {
    return source.stream()
        .map(InsightService::value)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
        .entrySet()
        .stream()
        .sorted(
            Map.Entry.<String, Long>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(limit)
        .map(e -> new CategoryStatisticsDto.LabelCount(e.getKey(), e.getValue()))
        .toList();
  }

  private static String value(String s) {
    return s == null || s.isBlank() ? OTHER : s.trim();
  }

  private static String normal(String s) {
    return Normalizer.normalize(value(s), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
  }
}
