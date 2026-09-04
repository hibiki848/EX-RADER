package com.exradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * Stripe Webhookの二重処理防止用。処理を始める前にstripeEventIdでこのテーブルへINSERTを試み、
 * UNIQUE制約違反(=既に受信済みの同一イベント再送)なら既存行のstatusを見て再処理するかどうかを
 * 判定する(StripeWebhookEventRepository.save()のDataIntegrityViolationExceptionを
 * StripeWebhookController側で捕捉して判定する)。
 *
 * statusはRECEIVED→PROCESSING→PROCESSED、または PROCESSING→FAILED と遷移する。
 * PROCESSED以外(再送時点でFAILEDなど)は再処理の対象とする(「受信済み」であることと
 * 「正常に処理済み」であることを混同しない)。
 */
@Entity
@Table(
    name = "stripe_webhook_events",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_stripe_webhook_events_event_id", columnNames = "stripe_event_id"))
public class StripeWebhookEvent extends BaseEntity {
  @Column(name = "stripe_event_id", nullable = false)
  private String stripeEventId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Column(name = "processed_at", nullable = false)
  private LocalDateTime processedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private StripeWebhookEventStatus status = StripeWebhookEventStatus.RECEIVED;

  protected StripeWebhookEvent() {}

  public StripeWebhookEvent(String stripeEventId, String eventType, LocalDateTime processedAt) {
    this.stripeEventId = stripeEventId;
    this.eventType = eventType;
    this.processedAt = processedAt;
  }

  public void markProcessing() {
    status = StripeWebhookEventStatus.PROCESSING;
  }

  public void markProcessed(LocalDateTime at) {
    status = StripeWebhookEventStatus.PROCESSED;
    processedAt = at;
  }

  public void markFailed(LocalDateTime at) {
    status = StripeWebhookEventStatus.FAILED;
    processedAt = at;
  }

  public String getStripeEventId() {
    return stripeEventId;
  }

  public String getEventType() {
    return eventType;
  }

  public LocalDateTime getProcessedAt() {
    return processedAt;
  }

  public StripeWebhookEventStatus getStatus() {
    return status;
  }
}
