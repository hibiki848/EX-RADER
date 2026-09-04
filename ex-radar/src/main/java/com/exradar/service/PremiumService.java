package com.exradar.service;

import com.exradar.entity.BenefitStatus;
import com.exradar.entity.PlanType;
import com.exradar.entity.User;
import com.exradar.entity.UserBenefit;
import com.exradar.exception.PremiumOperationException;
import com.exradar.exception.ResourceNotFoundException;
import com.exradar.repository.UserBenefitRepository;
import com.exradar.repository.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * プレミアム加入・特典利用のオーケストレーション。EXレーダー側とStripe側の責務分離を保つため、
 * 「保有特典の状態」の正はUserBenefit(BenefitService)、「実際の課金・割引・決済成功可否」の
 * 正はStripe(StripeService)とし、このクラスはその橋渡しのみを行う。
 *
 * Stripe API呼び出しはDBトランザクションの外側で行う(外部APIをトランザクション内で
 * 長時間保持しない)。予約(RESERVED)や状態更新はStripe呼び出しの前後で別々の
 * 短いトランザクションとして行う。
 */
@Service
public class PremiumService {
  private final UserRepository users;
  private final UserBenefitRepository userBenefits;
  private final BenefitService benefits;
  private final StripeService stripe;

  public PremiumService(
      UserRepository users, UserBenefitRepository userBenefits, BenefitService benefits, StripeService stripe) {
    this.users = users;
    this.userBenefits = userBenefits;
    this.benefits = benefits;
    this.stripe = stripe;
  }

  public record CheckoutStart(String redirectUrl) {}

  /**
   * 新規プレミアム加入(まだStripe Subscriptionを持っていないユーザー向け)。userBenefitIdが
   * 指定されていれば、対応する特典をRESERVEDにしたうえでCheckoutにCouponを適用する。
   * 実際にAVAILABLEを検証するのはBenefitService.reserve内(サーバー側のみで判断し、
   * クライアント入力の割引内容は一切信用しない)。
   */
  public CheckoutStart startNewSubscriptionCheckout(
      String email, Long userBenefitId, String successUrl, String cancelUrl) {
    var user = requireUser(email);
    UserBenefit reserved = null;
    try {
      String couponId = null;
      if (userBenefitId != null) {
        reserved = benefits.reserve(user.getId(), userBenefitId);
        couponId = stripe.resolveCouponId(reserved.getStripeCouponKeySnapshot());
      }
      String customerId =
          stripe.ensureCustomer(user.getEmail(), user.getDisplayName(), user.getStripeCustomerId());
      persistCustomerId(user.getId(), customerId);

      Session session =
          stripe.createSubscriptionCheckoutSession(
              customerId, couponId, successUrl, cancelUrl, reserved == null ? null : reserved.getId());
      if (reserved != null) benefits.attachCheckoutSession(reserved.getId(), session.getId());
      return new CheckoutStart(session.getUrl());
    } catch (StripeException e) {
      if (reserved != null) benefits.releaseReservation(reserved.getId());
      throw new PremiumOperationException("Stripeとの通信に失敗しました。時間をおいて再度お試しください。", e);
    } catch (RuntimeException e) {
      // Stripe Coupon未設定(IllegalStateException)等、予期しない失敗も含めて特典の予約は
      // 必ず解放する(Checkoutを開始できなかっただけで、特典を消費済みにはしない)。
      // GlobalExceptionHandlerの汎用500ページへ落とさず、PremiumControllerがフラッシュ
      // メッセージとして案内できるよう、必ずPremiumOperationExceptionへ包んで投げる。
      if (reserved != null) benefits.releaseReservation(reserved.getId());
      throw new PremiumOperationException("現在この操作をご利用いただけません。時間をおいて再度お試しください。", e);
    }
  }

  /**
   * 既にStripe Subscriptionを持つユーザーが特典を使う場合。Checkoutを経由せず、
   * Subscriptionへ直接Couponを設定する(次回請求にのみ適用。即時の日割り返金は行わない)。
   * Stripe API呼び出しを挟むため、このメソッド自体はトランザクションを張らない
   * (予約・確定それぞれの単純な状態更新はBenefitService側の個別トランザクションで行う)。
   */
  public void applyBenefitToExistingSubscription(String email, Long userBenefitId) {
    var user = requireUser(email);
    if (user.getStripeSubscriptionId() == null)
      throw new PremiumOperationException("現在プレミアムに加入していないため、この操作は行えません。");
    var reserved = benefits.reserve(user.getId(), userBenefitId);
    try {
      String couponId = stripe.resolveCouponId(reserved.getStripeCouponKeySnapshot());
      Subscription updated = stripe.applyCouponToSubscription(user.getStripeSubscriptionId(), couponId);
      benefits.markApplied(reserved.getId(), null, updated.getId());
    } catch (StripeException e) {
      benefits.releaseReservation(reserved.getId());
      throw new PremiumOperationException("Stripeとの通信に失敗しました。時間をおいて再度お試しください。", e);
    } catch (RuntimeException e) {
      benefits.releaseReservation(reserved.getId());
      throw new PremiumOperationException("現在この操作をご利用いただけません。時間をおいて再度お試しください。", e);
    }
  }

  /**
   * ユーザーがマイページの手続きをキャンセルした場合(cancel_urlへの復帰)。
   * cancel_urlへ戻ったこと自体はStripe Checkout Sessionが完全に失効したことを意味しない
   * (ブラウザの「戻る」やタブ複製等で古いCheckout Sessionがまだ有効なまま残りうる)ため、
   * 「cancel_urlに戻った」というブラウザ操作だけを理由に特典を即座にAVAILABLEへ戻さない。
   * 代わりにサーバー側からStripe Checkout Session自体をexpireさせ、それが成功した場合のみ
   * AVAILABLEへ戻す(1: Stripe Session expire API成功 → 2: UserBenefitをAVAILABLEへ、の順序)。
   * 失敗した場合は何もしない(その後のcheckout.session.expired Webhook、または
   * BenefitServiceの安全側フォールバックに委ねる)。
   */
  public void cancelCheckout(String email, Long benefitId) {
    var user = requireUser(email);
    var benefit = benefits.getOwned(user.getId(), benefitId);
    if (benefit.getStatus() != BenefitStatus.RESERVED) return;
    String sessionId = benefit.getStripeCheckoutSessionId();
    if (sessionId == null) return;
    try {
      stripe.expireCheckoutSession(sessionId);
    } catch (StripeException e) {
      // 既にStripe側で完了・失効済み等で失敗することがある。ここでは特典を勝手に
      // AVAILABLEへ戻さない(要件どおり)。実際に失効していれば後続のcheckout.session.expired
      // Webhookが届いて解放されるか、届かなければBenefitServiceの安全側フォールバックに委ねる。
      return;
    }
    benefits.releaseReservationByCheckoutSession(sessionId);
  }

  // ------------------------------------------------------------------
  // Webhookからの通知を受けての確定処理。到着順に依存せず、それぞれが
  // Stripeオブジェクトが持つID(Customer/Subscription/Checkout Session)から
  // 対象を特定できるようにする。
  // ------------------------------------------------------------------

  /**
   * checkout.session.completedでは、Checkout完了に伴う情報同期(Customer/Subscription IDの
   * 保存、対象特典のAPPLIED化)だけを行う。プレミアム利用権そのものの有効化はここでは行わない
   * (checkout.session.completedの時点では実際の請求成功がまだ確定していないため。
   * 有効化の基準はonInvoicePaidに一本化する)。
   */
  @Transactional
  public void onCheckoutSessionCompleted(Session session) {
    String customerId = session.getCustomer();
    String subscriptionId = session.getSubscription();
    if (customerId != null) {
      users.findByStripeCustomerId(customerId)
          .ifPresent(user -> {
            if (subscriptionId != null) user.updateStripeSubscriptionId(subscriptionId);
          });
    }
    userBenefits
        .findByStripeCheckoutSessionId(session.getId())
        .filter(b -> b.getStatus() == BenefitStatus.RESERVED)
        .ifPresent(b -> b.markApplied(LocalDateTime.now(), session.getId(), subscriptionId));
  }

  /**
   * Stripe側でCheckout Session自体が失効したことが確認できた場合。RESERVEDのままなら
   * AVAILABLEへ戻す(既にAPPLIED/USED/REVOKED等へ進んでいれば何もしない、冪等)。
   */
  @Transactional
  public void onCheckoutSessionExpired(Session session) {
    benefits.releaseReservationByCheckoutSession(session.getId());
  }

  /**
   * プレミアム利用権の有効化・特典のUSED確定は、いずれもinvoice.paidを基準にする
   * (checkout.session.completedだけでは行わない)。Stripe Subscriptionが実際に
   * 利用可能な状態(active/trialing)であることをStripe側へ再確認したうえで確定させる。
   * Subscriptionの状態確認はStripe APIを呼ぶため、DBトランザクションの外側で行う
   * (このメソッド自体は@Transactionalにしない。DB更新は個別の短いトランザクションに分ける)。
   *
   * 特典のUSED確定(markUsedBySubscription)は「APPLIED状態である」というだけでなく、
   * invoice.paidが持つStripe Subscription IDとUserBenefit.stripeSubscriptionIdが一致する
   * ことを必須条件にする(BenefitService側で再確認する)。これにより、古いAPPLIED特典が
   * 何らかの理由で残留していても、無関係な(別Subscriptionの)invoice.paidによって
   * 誤ってUSEDにされることはない。
   *
   * プレミアム有効化も同様に、解決したユーザーの既存stripeSubscriptionIdが今回のもの以外の
   * 値で既に設定されている場合は上書きしない(対象Subscriptionが現在のユーザーの
   * Subscriptionと一致することの確認。null=未設定、または一致する場合のみ許可する)。
   */
  public void onInvoicePaid(Invoice invoice) {
    String subscriptionId = subscriptionIdOf(invoice);
    if (subscriptionId == null) return;
    if (!isSubscriptionUsable(subscriptionId)) return;

    resolveUserForSubscription(invoice.getCustomer(), subscriptionId)
        .filter(user -> user.getStripeSubscriptionId() == null || subscriptionId.equals(user.getStripeSubscriptionId()))
        .ifPresent(user -> activatePremium(user.getId(), subscriptionId));
    benefits.markUsedBySubscription(subscriptionId, invoice.getId());
  }

  private boolean isSubscriptionUsable(String subscriptionId) {
    try {
      var subscription = stripe.retrieveSubscription(subscriptionId);
      return subscription != null
          && ("active".equals(subscription.getStatus()) || "trialing".equals(subscription.getStatus()));
    } catch (StripeException e) {
      return false;
    }
  }

  /** findByIdとsaveを同一メソッド内で完結させる(自己呼び出しによるプロキシ非適用を避けるため、persistCustomerIdと同じ方針)。 */
  private void activatePremium(Long userId, String subscriptionId) {
    users
        .findById(userId)
        .ifPresent(
            u -> {
              u.updateStripeSubscriptionId(subscriptionId);
              u.changePlan(PlanType.PREMIUM, LocalDateTime.now());
              users.save(u);
            });
  }

  /**
   * 決済失敗では特典をUSEDにしない(APPLIEDのまま維持)。Stripe側の支払い再試行で
   * 後日invoice.paidが届けば、その時点で正しくUSEDになる。プラン自体の自動ダウングレードも
   * ここでは行わない(Stripeの再試行・猶予期間の設計に委ね、実際に解約されたかどうかは
   * customer.subscription.deleted/updatedの到着で判断する)。
   */
  @Transactional
  public void onInvoicePaymentFailed(Invoice invoice) {
    // 意図的に何もしない(要件どおり: 決済失敗だけを理由に特典やプランの状態を変更しない)。
  }

  @Transactional
  public void onSubscriptionUpdated(Subscription subscription) {
    resolveUserForSubscription(subscription.getCustomer(), subscription.getId())
        .ifPresent(
            user -> {
              user.updateStripeSubscriptionId(subscription.getId());
              boolean active = "active".equals(subscription.getStatus()) || "trialing".equals(subscription.getStatus());
              user.changePlan(active ? PlanType.PREMIUM : PlanType.FREE, LocalDateTime.now());
            });
  }

  @Transactional
  public void onSubscriptionDeleted(Subscription subscription) {
    resolveUserForSubscription(subscription.getCustomer(), subscription.getId())
        .ifPresent(
            user -> {
              user.changePlan(PlanType.FREE, LocalDateTime.now());
              user.updateStripeSubscriptionId(null);
            });
  }

  private java.util.Optional<User> resolveUserForSubscription(String customerId, String subscriptionId) {
    var bySub = subscriptionId == null ? java.util.Optional.<User>empty() : users.findByStripeSubscriptionId(subscriptionId);
    if (bySub.isPresent()) return bySub;
    return customerId == null ? java.util.Optional.empty() : users.findByStripeCustomerId(customerId);
  }

  private String subscriptionIdOf(Invoice invoice) {
    var parent = invoice.getParent();
    if (parent == null || parent.getSubscriptionDetails() == null) return null;
    return parent.getSubscriptionDetails().getSubscription();
  }

  private User requireUser(String email) {
    return users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResourceNotFoundException("ユーザーが見つかりません"));
  }

  /**
   * findByIdとsaveを同一メソッド内で完結させ、明示的にsave()する(このメソッド自体は
   * @Transactionalを付けない外側の呼び出し元から使われるため、自己呼び出しによる
   * プロキシ非適用でdirty checkingが効かない事故を避けるため、更新はここでsave()により確定させる)。
   */
  private void persistCustomerId(Long userId, String customerId) {
    users
        .findById(userId)
        .ifPresent(
            u -> {
              u.assignStripeCustomerId(customerId);
              users.save(u);
            });
  }
}
