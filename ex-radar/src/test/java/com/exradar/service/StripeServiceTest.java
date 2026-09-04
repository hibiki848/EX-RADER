package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * StripeService内のCheckout Sessionパラメータ組み立て(Stripe APIへは接続しない部分)の検証。
 * Session.create自体はStripe SDKの静的メソッドでありJavaの単体テストからは容易に差し替えられない
 * ため、パラメータの組み立てだけをbuildSubscriptionCheckoutSessionParams(package-private)へ
 * 切り出し、実際にAPIを呼ばずに内容を検証できるようにしている。
 */
class StripeServiceTest {
  private final StripeService service =
      new StripeService(
          "sk_test_dummy", "whsec_dummy", "price_dummy", "coupon_20", "coupon_30", "coupon_50", "coupon_free");

  @Test
  void checkoutSessionExpiresThirtyMinutesAfterCreation() {
    long before = Instant.now().plusSeconds(30 * 60).getEpochSecond();
    SessionCreateParams params =
        service.buildSubscriptionCheckoutSessionParams(
            "cus_1", "coupon_1", "https://example.com/success", "https://example.com/cancel", null);
    long after = Instant.now().plusSeconds(30 * 60).getEpochSecond();

    assertThat(params.getExpiresAt()).isNotNull();
    assertThat(params.getExpiresAt()).isBetween(before, after + 1);
  }

  @Test
  void checkoutSessionAlwaysCollectsPaymentMethod() {
    SessionCreateParams params =
        service.buildSubscriptionCheckoutSessionParams(
            "cus_1", null, "https://example.com/success", "https://example.com/cancel", null);

    assertThat(params.getPaymentMethodCollection()).isEqualTo(SessionCreateParams.PaymentMethodCollection.ALWAYS);
  }

  @Test
  void checkoutSessionModeIsAlwaysSubscription() {
    SessionCreateParams params =
        service.buildSubscriptionCheckoutSessionParams(
            "cus_1", null, "https://example.com/success", "https://example.com/cancel", null);

    assertThat(params.getMode()).isEqualTo(SessionCreateParams.Mode.SUBSCRIPTION);
  }
}
