package com.exradar.controller;

import com.exradar.entity.StripeWebhookEvent;
import com.exradar.entity.StripeWebhookEventStatus;
import com.exradar.repository.StripeWebhookEventRepository;
import com.exradar.service.PremiumService;
import com.exradar.service.StripeService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Stripe Webhookの受信口。success_urlへの復帰を最終判定に使わないという方針どおり、
 * プレミアム有効化・特典USED確定は必ずこの経路(のPremiumService呼び出し)を通じてのみ行う。
 *
 * 二重処理防止: stripe_webhook_eventsのstatusで「受信済み」と「正常処理済み」を区別する。
 * 業務処理(dispatch)が例外で失敗した場合はFAILEDとして記録し、Stripeが同じイベントIDを
 * 再送してきたときにPROCESSED以外は再処理を許可する(受信済み≠正常処理済み。単に行が
 * 存在するというだけで「処理済み」と誤判定し、再送されても永久に処理されない事故を防ぐ)。
 * 新規イベントの初回INSERTはUNIQUE制約を使い、同時到達などの競合時はスキップ側に倒す。
 *
 * HTTPレスポンス方針: このアプリはバックグラウンドキューや独自の再試行スケジューラを
 * 持たないため、再試行はStripe自身のWebhook自動再送機能に委ねる。Stripeは2xx以外の
 * レスポンスを「配信失敗」とみなして自動的に再送してくるため、業務処理が失敗した場合は
 * 必ず5xxを返す(200を返して自前で再送を諦める、という設計は取らない)。
 * 正常処理・重複スキップは2xx、署名不正・不正リクエストは4xx、業務処理失敗は5xx。
 */
@Controller
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {
  private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

  private enum Decision {
    PROCESS,
    SKIP
  }

  private final StripeService stripe;
  private final PremiumService premium;
  private final StripeWebhookEventRepository events;

  public StripeWebhookController(StripeService stripe, PremiumService premium, StripeWebhookEventRepository events) {
    this.stripe = stripe;
    this.premium = premium;
    this.events = events;
  }

  @PostMapping
  public ResponseEntity<String> receive(
      HttpServletRequest request, @RequestHeader("Stripe-Signature") String signatureHeader) throws IOException {
    String payload = new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

    com.stripe.model.Event event;
    try {
      event = stripe.parseWebhookEvent(payload, signatureHeader);
    } catch (SignatureVerificationException e) {
      log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature");
    }

    if (beginProcessing(event.getId(), event.getType()) == Decision.SKIP) {
      log.info("Stripe webhook event already processed, skipping: id={} type={}", event.getId(), event.getType());
      return ResponseEntity.ok("duplicate");
    }

    try {
      dispatch(event);
    } catch (RuntimeException e) {
      // FAILEDの記録(markFailed)はJpaRepository#saveの独立したトランザクションで
      // 即座にコミットされる(このメソッド自体に@Transactionalは付けていないため、
      // ここで5xxを返してもFAILEDの記録がロールバックされることはない)。
      // 5xxを返すことでStripe自身のWebhook自動再送に処理をやり直させる
      // (自前の再試行キュー・スケジューラは持たないため、200を返して諦める設計は取らない)。
      log.error("Stripe webhook processing failed, returning 5xx so Stripe retries: id={} type={}",
          event.getId(), event.getType(), e);
      markFailed(event.getId());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("processing failed");
    }
    markProcessed(event.getId());
    return ResponseEntity.ok("ok");
  }

  private void dispatch(com.stripe.model.Event event) {
    var deserialized = event.getDataObjectDeserializer().getObject();
    switch (event.getType()) {
      case "checkout.session.completed" -> deserialized
          .filter(Session.class::isInstance)
          .map(Session.class::cast)
          .ifPresent(premium::onCheckoutSessionCompleted);
      case "checkout.session.expired" -> deserialized
          .filter(Session.class::isInstance)
          .map(Session.class::cast)
          .ifPresent(premium::onCheckoutSessionExpired);
      case "invoice.paid" -> deserialized
          .filter(Invoice.class::isInstance)
          .map(Invoice.class::cast)
          .ifPresent(premium::onInvoicePaid);
      case "invoice.payment_failed" -> deserialized
          .filter(Invoice.class::isInstance)
          .map(Invoice.class::cast)
          .ifPresent(premium::onInvoicePaymentFailed);
      case "customer.subscription.updated" -> deserialized
          .filter(Subscription.class::isInstance)
          .map(Subscription.class::cast)
          .ifPresent(premium::onSubscriptionUpdated);
      case "customer.subscription.deleted" -> deserialized
          .filter(Subscription.class::isInstance)
          .map(Subscription.class::cast)
          .ifPresent(premium::onSubscriptionDeleted);
      default -> log.debug("Unhandled Stripe webhook event type: {}", event.getType());
    }
  }

  /**
   * 初めて受信したイベント、またはPROCESSED以外(FAILED等)で記録されている既存イベントは
   * PROCESSに倒す(=処理する)。既にPROCESSEDのイベントはSKIP。
   * JpaRepository#save自体が1回のリポジトリ呼び出しとして完結したトランザクションで
   * 実行されるため、ここで別途@Transactionalを付ける必要はない(privateメソッドへ
   * 付けてもSpringのプロキシは適用されない点にも注意)。
   */
  private Decision beginProcessing(String eventId, String eventType) {
    var existing = events.findByStripeEventId(eventId);
    if (existing.isPresent()) {
      var row = existing.get();
      if (row.getStatus() == StripeWebhookEventStatus.PROCESSED) return Decision.SKIP;
      row.markProcessing();
      events.save(row);
      return Decision.PROCESS;
    }
    try {
      var row = new StripeWebhookEvent(eventId, eventType, LocalDateTime.now());
      row.markProcessing();
      events.save(row);
      return Decision.PROCESS;
    } catch (DataIntegrityViolationException e) {
      // 同時到達等でこの間に他リクエストが先にINSERTした場合。安全側に倒してスキップし、
      // 次回の再送で改めてstatusベースの判定に委ねる。
      return Decision.SKIP;
    }
  }

  private void markProcessed(String eventId) {
    events.findByStripeEventId(eventId).ifPresent(e -> {
      e.markProcessed(LocalDateTime.now());
      events.save(e);
    });
  }

  private void markFailed(String eventId) {
    events.findByStripeEventId(eventId).ifPresent(e -> {
      e.markFailed(LocalDateTime.now());
      events.save(e);
    });
  }
}
