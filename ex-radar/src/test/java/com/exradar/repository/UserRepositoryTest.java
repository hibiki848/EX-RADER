package com.exradar.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * アクセス解析(GA4・EXレーダー内部)集計向けの除外ロジックの検証。
 * AdminAnalyticsServiceは結果を5分間キャッシュするため、HTTP経由のテストでは
 * 他テストの実行順に依存して不安定になりうる。ここではリポジトリのクエリ自体を
 * @DataJpaTestで直接検証し、キャッシュの影響を受けないようにする。
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {
  @Autowired UserRepository users;

  @Test
  void countAnalyticsEligibleExcludesAdminsAndExcludedAccounts() {
    users.save(new User("eligible1@example.com", "hash", "対象1", Role.USER));
    users.save(new User("eligible2@example.com", "hash", "対象2", Role.USER));
    users.save(new User("admin@example.com", "hash", "管理者", Role.ADMIN));
    var excluded = users.save(new User("excluded@example.com", "hash", "除外", Role.USER));
    excluded.setAnalyticsExcluded(true);

    assertThat(users.countAnalyticsEligible(Role.ADMIN)).isEqualTo(2);
  }

  @Test
  void countAnalyticsEligibleSinceOnlyCountsRecentEligibleAccounts() {
    var recent = users.save(new User("recent@example.com", "hash", "最近登録", Role.USER));
    users.save(new User("admin@example.com", "hash", "管理者", Role.ADMIN));
    var excluded = users.save(new User("excluded@example.com", "hash", "除外", Role.USER));
    excluded.setAnalyticsExcluded(true);

    LocalDateTime since = LocalDateTime.now().minusDays(1);
    assertThat(users.countAnalyticsEligibleSince(Role.ADMIN, since)).isEqualTo(1);
    assertThat(recent).isNotNull();
  }

  @Test
  void reEnablingAnalyticsBringsUserBackIntoTheEligibleCount() {
    var user = users.save(new User("toggle@example.com", "hash", "切替対象", Role.USER));
    user.setAnalyticsExcluded(true);
    assertThat(users.countAnalyticsEligible(Role.ADMIN)).isZero();

    user.setAnalyticsExcluded(false);
    assertThat(users.countAnalyticsEligible(Role.ADMIN)).isEqualTo(1);
  }
}
