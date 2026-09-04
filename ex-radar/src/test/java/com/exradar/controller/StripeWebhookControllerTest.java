package com.exradar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.exradar.entity.StripeWebhookEventStatus;
import com.exradar.repository.StripeWebhookEventRepository;
import com.exradar.service.PremiumService;
import com.exradar.service.StripeService;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stripe Webhook受信口(StripeWebhookController)の検証。実際のStripe署名検証は
 * StripeServiceを@MockBeanで差し替えることで迂回し(署名検証ロジック自体はStripe SDK側の
 * 実装であり、ここではEXレーダー側の受信・振り分け・二重処理防止のみを検証する)、
 * 実際の処理はPremiumServiceも@MockBeanにして呼び出し回数だけを確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StripeWebhookControllerTest {
  @Autowired MockMvc mvc;
  @Autowired StripeWebhookEventRepository events;

  @MockBean StripeService stripeService;
  @MockBean PremiumService premiumService;

  private Event fakeCheckoutCompletedEvent(String eventId) {
    var session = mock(Session.class);
    var deserializer = mock(EventDataObjectDeserializer.class);
    when(deserializer.getObject()).thenReturn(java.util.Optional.of(session));
    var event = mock(Event.class);
    when(event.getId()).thenReturn(eventId);
    when(event.getType()).thenReturn("checkout.session.completed");
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);
    return event;
  }

  @Test
  void validEventIsDispatchedToPremiumServiceAndRecorded() throws Exception {
    var event = fakeCheckoutCompletedEvent("evt_test_dispatch_1");
    when(stripeService.parseWebhookEvent(anyString(), anyString())).thenReturn(event);

    mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", "test-sig").content("{}"))
        .andExpect(status().isOk());

    verify(premiumService, times(1)).onCheckoutSessionCompleted(any());
    org.assertj.core.api.Assertions.assertThat(events.existsByStripeEventId("evt_test_dispatch_1")).isTrue();
  }

  /** 同一イベントIDが再送されても、2回目以降は処理をスキップする(二重処理防止)。 */
  @Test
  void duplicateEventIsNotProcessedTwice() throws Exception {
    var event = fakeCheckoutCompletedEvent("evt_test_duplicate_1");
    when(stripeService.parseWebhookEvent(anyString(), anyString())).thenReturn(event);

    mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", "test-sig").content("{}"))
        .andExpect(status().isOk());
    mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", "test-sig").content("{}"))
        .andExpect(status().isOk());

    verify(premiumService, times(1)).onCheckoutSessionCompleted(any());
  }

  @Test
  void invalidSignatureIsRejectedWithoutDispatching() throws Exception {
    when(stripeService.parseWebhookEvent(anyString(), anyString()))
        .thenThrow(mock(com.stripe.exception.SignatureVerificationException.class));

    mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", "bad-sig").content("{}"))
        .andExpect(status().isBadRequest());

    verify(premiumService, never()).onCheckoutSessionCompleted(any());
  }

  @Test
  void checkoutSessionExpiredEventIsDispatchedToPremiumService() throws Exception {
    var session = mock(Session.class);
    var deserializer = mock(EventDataObjectDeserializer.class);
    when(deserializer.getObject()).thenReturn(java.util.Optional.of(session));
    var event = mock(Event.class);
    when(event.getId()).thenReturn("evt_test_expired_1");
    when(event.getType()).thenReturn("checkout.session.expired");
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);
    when(stripeService.parseWebhookEvent(anyString(), anyString())).thenReturn(event);

    mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", "test-sig").content("{}"))
        .andExpect(status().isOk());

    verify(premiumService, times(1)).onCheckoutSessionExpired(any());
  }

  /**
   * 業務処理(PremiumService呼び出し)が例外で失敗した場合、独自の再試行キュー・スケジューラを
   * 持たないためStripe自身の自動再送に処理をやり直させる必要があり、5xxを返す
   * (200を返して自前で諦める設計は取らない)。そのイベントはFAILEDとして記録され、
   * 「処理済み」とは判定されない(受信済み≠正常処理済み)。
   */
  @Test
  void failedProcessingIsRecordedAsFailedAndReturnsServerError() throws Exception {
    var event = fakeCheckoutCompletedEvent("evt_test_fail_1");
    when(stripeService.parseWebhookEvent(anyString(), anyString())).thenReturn(event);
    doThrow(new RuntimeException("simulated failure")).when(premiumService).onCheckoutSessionCompleted(any());

    mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", "test-sig").content("{}"))
        .andExpect(status().is5xxServerError());

    var afterFailure = events.findByStripeEventId("evt_test_fail_1").orElseThrow();
    assertThat(afterFailure.getStatus()).isEqualTo(StripeWebhookEventStatus.FAILED);
  }

  /**
   * FAILEDとして記録されたイベントがStripeの自動再送で再び届いた場合、PROCESSEDではないため
   * 再処理を許可する。2回目に成功すればPROCESSEDへ確定し、2xxを返す。
   */
  @Test
  void failedEventIsReprocessedOnResendAndSucceedsTheSecondTime() throws Exception {
    var event = fakeCheckoutCompletedEvent("evt_test_retry_1");
    when(stripeService.parseWebhookEvent(anyString(), anyString())).thenReturn(event);
    doThrow(new RuntimeException("simulated failure"))
        .doNothing()
        .when(premiumService)
        .onCheckoutSessionCompleted(any());

    mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", "test-sig").content("{}"))
        .andExpect(status().is5xxServerError());
    var afterFirstAttempt = events.findByStripeEventId("evt_test_retry_1").orElseThrow();
    assertThat(afterFirstAttempt.getStatus()).isEqualTo(StripeWebhookEventStatus.FAILED);

    mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", "test-sig").content("{}"))
        .andExpect(status().is2xxSuccessful());

    verify(premiumService, times(2)).onCheckoutSessionCompleted(any());
    var afterSecondAttempt = events.findByStripeEventId("evt_test_retry_1").orElseThrow();
    assertThat(afterSecondAttempt.getStatus()).isEqualTo(StripeWebhookEventStatus.PROCESSED);
  }
}
