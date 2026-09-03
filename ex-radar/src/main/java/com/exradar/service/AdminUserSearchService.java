package com.exradar.service;

import com.exradar.dto.AdminUserSearchCriteria;
import com.exradar.dto.AdminUserSortField;
import com.exradar.dto.AdminUserSummaryDto;
import com.exradar.entity.PostStatus;
import com.exradar.entity.User;
import com.exradar.repository.AdminUserSpecifications;
import com.exradar.repository.ExperiencePostRepository;
import com.exradar.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理者のユーザー検索・セグメント抽出の再利用可能な入口。管理者ユーザー一覧はもちろん、
 * 今後の個別・一括メッセージ配信、ログイン時のお知らせ配信からも同じ
 * AdminUserSearchCriteriaをそのまま渡して同じロジックで対象ユーザーを取得できる
 * (「登録30日以内 AND 無料プラン AND 投稿0件」のような条件が、機能ごとに
 * 別々に再実装されることを防ぐのが狙い)。
 */
@Service
public class AdminUserSearchService {
  public static final int DEFAULT_PAGE_SIZE = 20;

  private final UserRepository users;
  private final ExperiencePostRepository posts;

  public AdminUserSearchService(UserRepository users, ExperiencePostRepository posts) {
    this.users = users;
    this.posts = posts;
  }

  @Transactional(readOnly = true)
  public Page<AdminUserSummaryDto> search(
      AdminUserSearchCriteria criteria, int page, AdminUserSortField sort, Sort.Direction direction) {
    var spec = AdminUserSpecifications.build(criteria);
    Page<User> result =
        users.search(spec, sort, direction, PageRequest.of(Math.max(0, page), DEFAULT_PAGE_SIZE));
    Map<Long, PostStats> stats = postStatsFor(result.getContent());
    return result.map(u -> toSummary(u, stats.getOrDefault(u.getId(), PostStats.EMPTY)));
  }

  /**
   * 現在の検索条件に一致する全ユーザーのID(ページングなし)。将来のメッセージ配信・
   * お知らせ配信で「現在の検索条件に一致する全員」を対象にする際に使う想定。
   * 一覧表示のような詳細情報は持たず、IDのみを返す(対象確定後、送信処理側が
   * 必要な範囲だけ改めてユーザー情報を取得すればよいため)。
   */
  @Transactional(readOnly = true)
  public List<Long> findAllMatchingUserIds(AdminUserSearchCriteria criteria) {
    return users.findAllMatchingIds(AdminUserSpecifications.build(criteria));
  }

  /** ページ内のユーザーだけを対象に、投稿数・初回/最新投稿日時をまとめて1回で取得する(N+1回避)。 */
  private Map<Long, PostStats> postStatsFor(List<User> pageUsers) {
    if (pageUsers.isEmpty()) return Map.of();
    var userIds = pageUsers.stream().map(User::getId).toList();
    Map<Long, PostStats> result = new HashMap<>();
    for (Object[] row : posts.aggregatePostStatsByAuthorIds(userIds, PostStatus.PUBLISHED)) {
      Long userId = (Long) row[0];
      long count = (Long) row[1];
      LocalDateTime first = (LocalDateTime) row[2];
      LocalDateTime last = (LocalDateTime) row[3];
      result.put(userId, new PostStats(count, first, last));
    }
    return result;
  }

  private AdminUserSummaryDto toSummary(User u, PostStats stats) {
    return AdminUserSummaryDto.from(u, stats.count(), stats.firstPostAt(), stats.lastPostAt());
  }

  private record PostStats(long count, LocalDateTime firstPostAt, LocalDateTime lastPostAt) {
    static final PostStats EMPTY = new PostStats(0, null, null);
  }
}
