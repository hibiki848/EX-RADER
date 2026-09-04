package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.exradar.entity.BenefitSourceType;
import com.exradar.entity.BenefitStatus;
import com.exradar.entity.PlanType;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.entity.UserBenefit;
import com.exradar.exception.PremiumOperationException;
import com.exradar.repository.BenefitDefinitionRepository;
import com.exradar.repository.UserBenefitRepository;
import com.exradar.repository.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * プレミアム加入・特典利用のオーケストレーション(PremiumService)の検証。
 * Stripe APIそのものへは依存させず、StripeServiceを@MockBeanで差し替えて検証する
 * (外部APIへ実際に接続するテストにはしない)。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PremiumServiceTest {
  @Autowired PremiumService premiumService;
  @Autowired BenefitService benefitService;
  @Autowired UserBenefitRepository userBenefits;
  @Autowired BenefitDefinitionRepository benefitDefinitions;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  @MockBean StripeService stripeService;

  private User newUser(String email) {
    return users.save(new User(email, encoder.encode("password"), "テストユーザー", Role.USER));
  }

  private UserBenefit grant(User user) {
    var definition = benefitDefinitions.findByCode("DISCOUNT_50").orElseThrow();
    var benefit =
        new UserBenefit(
            user, definition, BenefitSourceType.POST_MILESTONE, null, "テスト付与",
            "TEST:" + user.getId() + ":" + System.nanoTime(), LocalDateTime.now());
    return userBenefits.save(benefit);
  }

  @Test
  void startingCheckoutReservesTheBenefitAndAttachesTheCheckoutSession() throws StripeException {
    var user = newUser("premium-checkout-start@example.com");
    var benefit = grant(user);
    when(stripeService.resolveCouponId(anyString())).thenReturn("coupon_test_50");
    when(stripeService.ensureCustomer(anyString(), anyString(), any())).thenReturn("cus_test_1");
    var fakeSession = mock(Session.class);
    when(fakeSession.getId()).thenReturn("cs_test_1");
    when(fakeSession.getUrl()).thenReturn("https://checkout.stripe.com/test");
    when(stripeService.createSubscriptionCheckoutSession(any(), any(), any(), any(), any())).thenReturn(fakeSession);

    var result =
        premiumService.startNewSubscriptionCheckout(
            user.getEmail(), benefit.getId(), "https://example.com/success", "https://example.com/cancel");

    assertThat(result.redirectUrl()).isEqualTo("https://checkout.stripe.com/test");
    var reloaded = userBenefits.findById(benefit.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(BenefitStatus.RESERVED);
    assertThat(reloaded.getStripeCheckoutSessionId()).isEqualTo("cs_test_1");
  }

  @Test
  void stripeFailureDuringCheckoutReleasesTheReservationBackToAvailable() throws StripeException {
    var user = newUser("premium-checkout-failure@example.com");
    var benefit = grant(user);
    when(stripeService.resolveCouponId(anyString())).thenReturn("coupon_test_50");
    when(stripeService.ensureCustomer(anyString(), anyString(), any())).thenThrow(mock(StripeException.class));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                premiumService.startNewSubscriptionCheckout(
                    user.getEmail(), benefit.getId(), "https://example.com/success", "https://example.com/cancel"))
        .isInstanceOf(PremiumOperationException.class);

    var reloaded = userBenefits.findById(benefit.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(BenefitStatus.AVAILABLE);
  }

  /** checkout.session.completedは情報同期(Subscription ID保存・特典APPLIED化)のみ行い、プレミアム利用権はまだ有効化しない。 */
  @Test
  void checkoutSessionCompletedSyncsSubscriptionAndMarksBenefitAppliedButDoesNotActivatePremium() {
    var user = newUser("premium-checkout-completed@example.com");
    user.assignStripeCustomerId("cus_test_2");
    users.save(user);
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());
    benefitService.attachCheckoutSession(benefit.getId(), "cs_test_2");

    var session = mock(Session.class);
    when(session.getId()).thenReturn("cs_test_2");
    when(session.getCustomer()).thenReturn("cus_test_2");
    when(session.getSubscription()).thenReturn("sub_test_2");

    premiumService.onCheckoutSessionCompleted(session);

    var reloadedUser = users.findById(user.getId()).orElseThrow();
    assertThat(reloadedUser.getCurrentPlan()).isEqualTo(PlanType.FREE);
    assertThat(reloadedUser.getStripeSubscriptionId()).isEqualTo("sub_test_2");
    var reloadedBenefit = userBenefits.findById(benefit.getId()).orElseThrow();
    assertThat(reloadedBenefit.getStatus()).isEqualTo(BenefitStatus.APPLIED);
  }

  private Invoice fakeInvoiceForSubscription(String invoiceId, String customerId, String subscriptionId) {
    var invoice = mock(Invoice.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
    when(invoice.getId()).thenReturn(invoiceId);
    when(invoice.getCustomer()).thenReturn(customerId);
    when(invoice.getParent().getSubscriptionDetails().getSubscription()).thenReturn(subscriptionId);
    return invoice;
  }

  private Subscription activeSubscription(String subscriptionId) throws StripeException {
    var subscription = mock(Subscription.class);
    when(subscription.getStatus()).thenReturn("active");
    when(stripeService.retrieveSubscription(subscriptionId)).thenReturn(subscription);
    return subscription;
  }

  @Test
  void invoicePaidActivatesPremiumAndMarksTheAppliedBenefitAsUsed() throws StripeException {
    var user = newUser("premium-invoice-paid@example.com");
    user.assignStripeCustomerId("cus_test_3");
    user.updateStripeSubscriptionId("sub_test_3");
    users.save(user);
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());
    benefitService.markApplied(benefit.getId(), "cs_test_3", "sub_test_3");
    activeSubscription("sub_test_3");

    premiumService.onInvoicePaid(fakeInvoiceForSubscription("in_test_3", "cus_test_3", "sub_test_3"));

    var reloadedBenefit = userBenefits.findById(benefit.getId()).orElseThrow();
    assertThat(reloadedBenefit.getStatus()).isEqualTo(BenefitStatus.USED);
    assertThat(reloadedBenefit.getStripeInvoiceId()).isEqualTo("in_test_3");
    var reloadedUser = users.findById(user.getId()).orElseThrow();
    assertThat(reloadedUser.getCurrentPlan()).isEqualTo(PlanType.PREMIUM);
  }

  /** Stripe側でSubscriptionがまだ利用可能な状態(active/trialing)であることを確認できない限り、プレミアムを有効化しない。 */
  @Test
  void invoicePaidDoesNotActivatePremiumWhenSubscriptionIsNotUsable() throws StripeException {
    var user = newUser("premium-invoice-unusable@example.com");
    user.assignStripeCustomerId("cus_test_3b");
    user.updateStripeSubscriptionId("sub_test_3b");
    users.save(user);
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());
    benefitService.markApplied(benefit.getId(), "cs_test_3b", "sub_test_3b");
    var subscription = mock(Subscription.class);
    when(subscription.getStatus()).thenReturn("canceled");
    when(stripeService.retrieveSubscription("sub_test_3b")).thenReturn(subscription);

    premiumService.onInvoicePaid(fakeInvoiceForSubscription("in_test_3b", "cus_test_3b", "sub_test_3b"));

    var reloadedUser = users.findById(user.getId()).orElseThrow();
    assertThat(reloadedUser.getCurrentPlan()).isEqualTo(PlanType.FREE);
    var reloadedBenefit = userBenefits.findById(benefit.getId()).orElseThrow();
    assertThat(reloadedBenefit.getStatus()).isEqualTo(BenefitStatus.APPLIED);
  }

  /** invoice.paidが同じInvoiceについて再送されても、既にUSEDになった特典は二重消費されない(冪等)。 */
  @Test
  void invoicePaidIsIdempotentWhenReprocessedForTheSameInvoice() throws StripeException {
    var user = newUser("premium-invoice-idempotent@example.com");
    user.assignStripeCustomerId("cus_test_3c");
    user.updateStripeSubscriptionId("sub_test_3c");
    users.save(user);
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());
    benefitService.markApplied(benefit.getId(), "cs_test_3c", "sub_test_3c");
    activeSubscription("sub_test_3c");

    premiumService.onInvoicePaid(fakeInvoiceForSubscription("in_test_3c", "cus_test_3c", "sub_test_3c"));
    // 同じInvoiceについて再送された想定でもう一度呼ぶ。既にUSEDのため例外にならず、状態も変わらない。
    premiumService.onInvoicePaid(fakeInvoiceForSubscription("in_test_3c", "cus_test_3c", "sub_test_3c"));

    var reloaded = userBenefits.findById(benefit.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(BenefitStatus.USED);
  }

  /**
   * 古いAPPLIED特典が異常に残留していても、その特典が紐づくSubscription IDと
   * invoice.paidのSubscription IDが一致しなければUSEDにされない(「APPLIED状態である」
   * という条件だけに依存しないことの確認)。
   */
  @Test
  void invoicePaidDoesNotTouchAnAppliedBenefitTiedToADifferentSubscription() throws StripeException {
    var user = newUser("premium-invoice-mismatch@example.com");
    user.assignStripeCustomerId("cus_test_3d");
    user.updateStripeSubscriptionId("sub_test_3d_old");
    users.save(user);
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());
    // このユーザーの特典は古いSubscription(sub_test_3d_old)へAPPLIED済みのまま残留している想定。
    benefitService.markApplied(benefit.getId(), "cs_test_3d_old", "sub_test_3d_old");
    activeSubscription("sub_test_3d_new");

    // 別の(新しい)Subscriptionに対するinvoice.paidが届く。
    premiumService.onInvoicePaid(fakeInvoiceForSubscription("in_test_3d", "cus_test_3d", "sub_test_3d_new"));

    var reloaded = userBenefits.findById(benefit.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(BenefitStatus.APPLIED);
    assertThat(reloaded.getStripeInvoiceId()).isNull();
    // プレミアム有効化も、既存のSubscription IDと矛盾する場合は行わない(要件どおり)。
    var reloadedUser = users.findById(user.getId()).orElseThrow();
    assertThat(reloadedUser.getCurrentPlan()).isEqualTo(PlanType.FREE);
    assertThat(reloadedUser.getStripeSubscriptionId()).isEqualTo("sub_test_3d_old");
  }

  /** 無関係な他ユーザーのSubscriptionに対するinvoice.paidによって、既にUSED確定済みの特典が変更されないこと。 */
  @Test
  void invoicePaidForAnUnrelatedSubscriptionDoesNotAffectAnAlreadyUsedBenefit() throws StripeException {
    var user = newUser("premium-invoice-unrelated@example.com");
    user.assignStripeCustomerId("cus_test_3e");
    user.updateStripeSubscriptionId("sub_test_3e");
    users.save(user);
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());
    benefitService.markApplied(benefit.getId(), "cs_test_3e", "sub_test_3e");
    activeSubscription("sub_test_3e");
    premiumService.onInvoicePaid(fakeInvoiceForSubscription("in_test_3e_first", "cus_test_3e", "sub_test_3e"));
    assertThat(userBenefits.findById(benefit.getId()).orElseThrow().getStatus()).isEqualTo(BenefitStatus.USED);

    // 全く別のユーザー・別のSubscriptionに対するinvoice.paidが後から届いても、
    // 既にUSED確定済みのこの特典には触れない(そもそもクエリがAPPLIEDのみを対象にするため)。
    activeSubscription("sub_test_unrelated");
    premiumService.onInvoicePaid(fakeInvoiceForSubscription("in_test_unrelated", "cus_unrelated", "sub_test_unrelated"));

    var reloaded = userBenefits.findById(benefit.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(BenefitStatus.USED);
    assertThat(reloaded.getStripeInvoiceId()).isEqualTo("in_test_3e_first");
  }

  @Test
  void invoicePaymentFailedDoesNotChangeTheAppliedBenefitStatus() {
    var user = newUser("premium-invoice-failed@example.com");
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());
    benefitService.markApplied(benefit.getId(), "cs_test_4", "sub_test_4");

    var invoice = mock(Invoice.class);
    premiumService.onInvoicePaymentFailed(invoice);

    var reloaded = userBenefits.findById(benefit.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(BenefitStatus.APPLIED);
  }

  @Test
  void subscriptionDeletedDowngradesUserToFree() {
    var user = newUser("premium-sub-deleted@example.com");
    user.changePlan(PlanType.PREMIUM, LocalDateTime.now());
    user.updateStripeSubscriptionId("sub_test_5");
    users.save(user);

    var subscription = mock(Subscription.class);
    when(subscription.getId()).thenReturn("sub_test_5");
    when(subscription.getCustomer()).thenReturn("cus_test_5");

    premiumService.onSubscriptionDeleted(subscription);

    var reloaded = users.findById(user.getId()).orElseThrow();
    assertThat(reloaded.getCurrentPlan()).isEqualTo(PlanType.FREE);
    assertThat(reloaded.getStripeSubscriptionId()).isNull();
  }

  @Test
  void checkoutSessionExpiredReleasesAReservedBenefitBackToAvailable() {
    var user = newUser("premium-checkout-expired@example.com");
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());
    benefitService.attachCheckoutSession(benefit.getId(), "cs_test_expired_1");

    var session = mock(Session.class);
    when(session.getId()).thenReturn("cs_test_expired_1");
    premiumService.onCheckoutSessionExpired(session);

    var reloaded = userBenefits.findById(benefit.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(BenefitStatus.AVAILABLE);
  }

  /** 既にAPPLIEDへ進んでいる特典は、遅れて届いたcheckout.session.expiredによってRESERVEDへ逆戻りしない(冪等)。 */
  @Test
  void checkoutSessionExpiredDoesNotRevertAnAlreadyAppliedBenefit() {
    var user = newUser("premium-checkout-expired-applied@example.com");
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());
    benefitService.attachCheckoutSession(benefit.getId(), "cs_test_expired_2");
    benefitService.markApplied(benefit.getId(), "cs_test_expired_2", "sub_test_expired_2");

    var session = mock(Session.class);
    when(session.getId()).thenReturn("cs_test_expired_2");
    premiumService.onCheckoutSessionExpired(session);

    var reloaded = userBenefits.findById(benefit.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(BenefitStatus.APPLIED);
  }

  /** cancel_urlへの復帰(=ブラウザ操作)だけでは解放されない: Stripe Session expireが成功して初めてAVAILABLEへ戻る。 */
  @Test
  void cancelCheckoutReleasesOnlyAfterStripeSessionExpireSucceeds() throws StripeException {
    var user = newUser("premium-cancel-success@example.com");
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());
    benefitService.attachCheckoutSession(benefit.getId(), "cs_test_cancel_1");
    when(stripeService.expireCheckoutSession("cs_test_cancel_1")).thenReturn(mock(Session.class));

    premiumService.cancelCheckout(user.getEmail(), benefit.getId());

    var reloaded = userBenefits.findById(benefit.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(BenefitStatus.AVAILABLE);
  }

  @Test
  void cancelCheckoutDoesNotReleaseWhenStripeSessionExpireFails() throws StripeException {
    var user = newUser("premium-cancel-failure@example.com");
    var benefit = grant(user);
    benefitService.reserve(user.getId(), benefit.getId());
    benefitService.attachCheckoutSession(benefit.getId(), "cs_test_cancel_2");
    when(stripeService.expireCheckoutSession("cs_test_cancel_2")).thenThrow(mock(StripeException.class));

    premiumService.cancelCheckout(user.getEmail(), benefit.getId());

    var reloaded = userBenefits.findById(benefit.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(BenefitStatus.RESERVED);
  }
}
