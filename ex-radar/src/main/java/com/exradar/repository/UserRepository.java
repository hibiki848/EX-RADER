package com.exradar.repository;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository
    extends JpaRepository<User, Long>, JpaSpecificationExecutor<User>, AdminUserRepositoryCustom {
  Optional<User> findByEmailIgnoreCase(String email);

  @EntityGraph(attributePaths = "values")
  Optional<User> findWithValuesByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  long countByRole(Role role);

  long countBySuspendedTrue();

  long countByCreatedAtAfter(LocalDateTime since);

  // アクセス解析(GA4・EXレーダー内部)集計向け。管理者、および
  // analyticsExcluded=trueのユーザーを除いた「実際の利用者」だけを数える。
  @Query("select count(u) from User u where u.role <> :adminRole and u.analyticsExcluded = false")
  long countAnalyticsEligible(@Param("adminRole") Role adminRole);

  @Query(
      "select count(u) from User u where u.role <> :adminRole and u.analyticsExcluded = false"
          + " and u.createdAt >= :since")
  long countAnalyticsEligibleSince(
      @Param("adminRole") Role adminRole, @Param("since") LocalDateTime since);

  Optional<User> findByProviderUserId(String providerUserId);

  java.util.List<User> findAllByOrderByCreatedAtDesc();
}
