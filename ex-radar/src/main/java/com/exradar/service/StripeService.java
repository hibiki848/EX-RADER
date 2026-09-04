package com.exradar.service;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stripe SDKへの実際の呼び出しを1箇所に集約する薄いラッパー。EXレーダー側の業務ロジック
 * (特典の状態遷移・プラン変更の判断)はここには置かず、PremiumService/BenefitServiceが担う
 * (このクラスは「Stripeとどう話すか」だけに責務を絞り、テスト時にモックしやすくする)。
 *
 * Coupon IDはJavaコードへ直接ハードコードせず、benefit_definitions.stripe_coupon_key
 * (例: "DISCOUNT_20")を、環境変数/application.ymlのexradar.stripe.coupons.*で
 * 実際のStripe Coupon IDへ解決する。テスト環境・本番環境の切り替えは環境変数側で行う。
 */
@Service
public class StripeService {
  private final String webhookSecret;
  private final String premiumPriceId;
  private final Map<String, String> couponIdsByKey = new HashMap<>();

  public StripeService(
      @Value("${exradar.stripe.api-key:}") String apiKey,
      @Value("${exradar.stripe.webhook-secret:}") String webhookSecret,
      @Value("${exradar.stripe.premium-price-id:}") String premiumPriceId,
      @Value("${exradar.stripe.coupons.discount-20:}") String couponDiscount20,
      @Value("${exradar.stripe.coupons.discount-30:}") String couponDiscount30,
      @Value("${exradar.stripe.coupons.discount-50:}") String couponDiscount50,
      @Value("${exradar.stripe.coupons.free-month:}") String couponFreeMonth) {
    Stripe.apiKey = apiKey;
    this.webhookSecret = webhookSecret;
    this.premiumPriceId = premiumPriceId;
    couponIdsByKey.put("DISCOUNT_20", couponDiscount20);
    couponIdsByKey.put("DISCOUNT_30", couponDiscount30);
    couponIdsByKey.put("DISCOUNT_50", couponDiscount50);
    couponIdsByKey.put("FREE_MONTH", couponFreeMonth);
  }

  @PostConstruct
  void logConfigurationState() {
    // 未設定でもアプリ起動自体は妨げない(Stripe機能を使わないdev/test環境もあるため)。
    // 実際に決済系エンドポイントを呼んだ時点でIllegalStateExceptionとして顕在化する。
  }

  public boolean isConfigured() {
    return Stripe.apiKey != null && !Stripe.apiKey.isBlank();
  }

  /** benefit_definitions.stripe_coupon_key(例: "DISCOUNT_50")を環境ごとの実Coupon IDへ解決する。 */
  public String resolveCouponId(String couponKey) {
    String id = couponIdsByKey.get(couponKey);
    if (id == null || id.isBlank())
      throw new IllegalStateException("Stripe Couponが未設定です(key=" + couponKey + ")。環境変数を確認してください。");
    return id;
  }

  public String ensureCustomer(String email, String displayName, String existingCustomerId) throws StripeException {
    if (existingCustomerId != null && !existingCustomerId.isBlank()) return existingCustomerId;
    var params = CustomerCreateParams.builder().setEmail(email).setName(displayName).build();
    return Customer.create(params).getId();
  }

  /** Stripe Checkout Sessionの有効期限。EXレーダー側のRESERVED解放猶予(BenefitService)と一致させ、期限切れの判定基準を1つに揃える。 */
  public static final long CHECKOUT_SESSION_EXPIRY_MINUTES = 30;

  /**
   * 新規プレミアム加入用のCheckout Session(subscriptionモード)。couponIdを渡すと、
   * その1回の請求だけに割引を適用したうえで通常のSubscriptionをそのまま作成する
   * (無料期間終了後にSubscriptionを作り直す特殊な設計は取らない)。
   *
   * expires_atを作成時刻から30分後に明示する: EXレーダー側のRESERVED特典が(Webhookが
   * 届かなかった場合の最終フォールバックとして)一定時間後に解放される仕様と、Stripe
   * Checkout Session自体の有効期限を一致させ、EXレーダー側だけAVAILABLEに戻ったのに
   * 古いCheckout Sessionがまだ使える、という不整合を防ぐ。
   *
   * payment_method_collection=ALWAYSを明示する: プレミアム1か月無料(100%OFF)のように
   * 今回の請求額が0円になる場合でも、翌月以降の継続課金に必要な支払い方法を必ず登録させる
   * ため(Stripeのデフォルトでは金額0円のCheckoutで支払い方法の収集が省略されることがある)。
   */
  public Session createSubscriptionCheckoutSession(
      String customerId, String couponId, String successUrl, String cancelUrl, Long userBenefitId)
      throws StripeException {
    var params =
        buildSubscriptionCheckoutSessionParams(customerId, couponId, successUrl, cancelUrl, userBenefitId);
    return Session.create(params);
  }

  /** Stripe API呼び出しを伴わない、パラメータ組み立てだけを切り出したもの(単体テストで内容を検証できるようにするため)。 */
  SessionCreateParams buildSubscriptionCheckoutSessionParams(
      String customerId, String couponId, String successUrl, String cancelUrl, Long userBenefitId) {
    var builder =
        SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .setExpiresAt(Instant.now().plusSeconds(CHECKOUT_SESSION_EXPIRY_MINUTES * 60).getEpochSecond())
            .setPaymentMethodCollection(SessionCreateParams.PaymentMethodCollection.ALWAYS)
            .addLineItem(
                SessionCreateParams.LineItem.builder().setPrice(premiumPriceId).setQuantity(1L).build())
            .putMetadata("exradarPurpose", "premium_subscription");
    if (userBenefitId != null) builder.putMetadata("userBenefitId", String.valueOf(userBenefitId));
    if (couponId != null)
      builder.addDiscount(SessionCreateParams.Discount.builder().setCoupon(couponId).build());
    return builder.build();
  }

  public Session retrieveCheckoutSession(String sessionId) throws StripeException {
    return Session.retrieve(sessionId);
  }

  /**
   * Checkoutキャンセル操作等でサーバー側からStripe Checkout Sessionを明示的に失効させる。
   * 成功した場合のみ呼び出し元がUserBenefitをAVAILABLEへ戻せる(失敗時は何もしない)。
   */
  public Session expireCheckoutSession(String sessionId) throws StripeException {
    return Session.retrieve(sessionId).expire();
  }

  /**
   * 既にSubscriptionを持つユーザーが特典を使った場合。次回請求にのみ適用されるよう、
   * Stripe Coupon自体がduration=onceで作成されていることを前提とする(Stripe
   * Dashboard側の設定。手動確認が必要な項目として報告する)。
   */
  public Subscription applyCouponToSubscription(String subscriptionId, String couponId) throws StripeException {
    var subscription = Subscription.retrieve(subscriptionId);
    var params =
        SubscriptionUpdateParams.builder()
            .addDiscount(SubscriptionUpdateParams.Discount.builder().setCoupon(couponId).build())
            .build();
    return subscription.update(params);
  }

  public Subscription retrieveSubscription(String subscriptionId) throws StripeException {
    return Subscription.retrieve(subscriptionId);
  }

  /** 署名検証込みでWebhookペイロードをEventへ変換する。検証に失敗すると例外を投げる(呼び出し側は400を返す)。 */
  public Event parseWebhookEvent(String payload, String signatureHeader) throws SignatureVerificationException {
    return Webhook.constructEvent(payload, signatureHeader, webhookSecret);
  }
}
