package com.exradar.repository;

import com.exradar.entity.StripeWebhookEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, Long> {
  boolean existsByStripeEventId(String stripeEventId);

  Optional<StripeWebhookEvent> findByStripeEventId(String stripeEventId);
}
