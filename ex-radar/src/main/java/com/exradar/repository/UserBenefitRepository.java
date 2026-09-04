package com.exradar.repository;

import com.exradar.entity.BenefitStatus;
import com.exradar.entity.UserBenefit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBenefitRepository extends JpaRepository<UserBenefit, Long> {
  @EntityGraph(attributePaths = "benefitDefinition")
  List<UserBenefit> findByUserIdOrderByGrantedAtDesc(Long userId);

  boolean existsByRewardGrantKey(String rewardGrantKey);

  @EntityGraph(attributePaths = {"benefitDefinition", "user"})
  Optional<UserBenefit> findByIdAndUserId(Long id, Long userId);

  @EntityGraph(attributePaths = {"benefitDefinition", "user"})
  Optional<UserBenefit> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);

  /** 既存Subscriptionへ直接クーポンを適用した場合(Checkoutを経由しない)のWebhook側の突き合わせ用。 */
  @EntityGraph(attributePaths = {"benefitDefinition", "user"})
  List<UserBenefit> findByStripeSubscriptionIdAndStatus(String stripeSubscriptionId, BenefitStatus status);

  long countByUserIdAndStatus(Long userId, BenefitStatus status);

  void deleteByUserId(Long userId);
}
