package com.exradar.service;

import com.exradar.entity.BenefitStatus;
import com.exradar.entity.Role;
import com.exradar.entity.User;
import com.exradar.entity.UserBenefit;
import com.exradar.exception.ForbiddenOperationException;
import com.exradar.exception.ResourceNotFoundException;
import com.exradar.repository.UserBenefitRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserBenefitの状態遷移・所有者確認を担う。「特典を持っているという事実そのもの」を
 * 管理する層であり、Stripeそのものへの通信はStripeService/PremiumServiceの責務とする
 * (このクラスはStripe SDKに依存しない)。
 *
 * Checkout画面を開いただけで放置された(RESERVEDのまま戻ってこない)特典は、本来は
 * checkout.session.expired Webhook(StripeがCheckout Session自体を失効させたことの確認)を
 * 受けてBenefitService.releaseReservationByCheckoutSessionで解放するのが基本経路。
 * このSTALE_RESERVATION_TIMEOUTはWebhookが何らかの理由で届かなかった場合の、あくまで
 * 安全側のフォールバックに過ぎない(このアプリにスケジューラ(@Scheduled)の前例が無いため
 * 定期実行では解放せず、次にその特典を再度使おうとした・一覧取得した時点で判定する)。
 * Stripe Checkout SessionのexpiresAtは作成時刻から30分に固定しているため、ローカル側の
 * このタイムアウトはそれより長い35分とし、Webhook経由の解放が常に先に効くようにする
 * (Stripe側でまだ有効なCheckout SessionをローカルのタイマーだけでAVAILABLEへ戻さないため)。
 */
@Service
public class BenefitService {
  private static final Duration STALE_RESERVATION_TIMEOUT = Duration.ofMinutes(35);

  private final UserBenefitRepository userBenefits;

  public BenefitService(UserBenefitRepository userBenefits) {
    this.userBenefits = userBenefits;
  }

  @Transactional
  public List<UserBenefit> listFor(Long userId) {
    var list = userBenefits.findByUserIdOrderByGrantedAtDesc(userId);
    var now = LocalDateTime.now();
    for (var b : list) syncStaleState(b, now);
    return list;
  }

  /** ログインユーザー本人が保有する特典であることを確認して返す。他人のIDを指定した場合は404扱いにする(存在有無を教えない)。 */
  @Transactional(readOnly = true)
  public UserBenefit getOwned(Long userId, Long benefitId) {
    return userBenefits
        .findByIdAndUserId(benefitId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("特典が見つかりません"));
  }

  /** 「この特典を使う」操作の起点。AVAILABLEの特典のみRESERVEDにできる。 */
  @Transactional
  public UserBenefit reserve(Long userId, Long benefitId) {
    var benefit = getOwned(userId, benefitId);
    syncStaleState(benefit, LocalDateTime.now());
    benefit.reserve(LocalDateTime.now());
    return benefit;
  }

  /**
   * 直前に自分自身がRESERVEDにした特典を、Stripe呼び出し失敗等で即座に解放する場合に使う
   * (呼び出し元はRESERVEDであることを前提にしてよい状況でのみ使うこと。そうでない状態
   * (Webhook等、到着順に依存できない経路)からはreleaseReservationByCheckoutSessionを使う)。
   */
  @Transactional
  public void releaseReservation(Long benefitId) {
    userBenefits.findById(benefitId).ifPresent(UserBenefit::releaseReservation);
  }

  /**
   * checkout.session.expired等、Webhook経由でCheckout Sessionの失効が確認できたときに使う
   * 冪等な解放。RESERVED以外(既にAPPLIED/USED/REVOKED等へ進んでいる場合)は何もしない
   * (Webhookの到着順・再送に依存せず、不正な逆戻りをさせないため)。
   */
  @Transactional
  public void releaseReservationByCheckoutSession(String checkoutSessionId) {
    userBenefits
        .findByStripeCheckoutSessionId(checkoutSessionId)
        .ifPresent(UserBenefit::releaseReservationIfStillReserved);
  }

  /** Checkout Session作成直後、まだRESERVEDのままセッションIDだけを記録する(Webhook側の逆引き用)。 */
  @Transactional
  public void attachCheckoutSession(Long benefitId, String checkoutSessionId) {
    userBenefits.findById(benefitId).ifPresent(b -> b.attachCheckoutSession(checkoutSessionId));
  }

  @Transactional
  public void markApplied(Long benefitId, String checkoutSessionId, String subscriptionId) {
    userBenefits
        .findById(benefitId)
        .orElseThrow(() -> new ResourceNotFoundException("特典が見つかりません"))
        .markApplied(LocalDateTime.now(), checkoutSessionId, subscriptionId);
  }

  /** checkout.session.completedのWebhookから、metadata経由ではなくstripe_checkout_session_idで突き合わせる(到着順に依存しないための経路の一つ)。 */
  @Transactional
  public void markAppliedByCheckoutSession(String checkoutSessionId, String subscriptionId) {
    userBenefits
        .findByStripeCheckoutSessionId(checkoutSessionId)
        .filter(b -> b.getStatus() == BenefitStatus.RESERVED)
        .ifPresent(b -> b.markApplied(LocalDateTime.now(), checkoutSessionId, subscriptionId));
  }

  @Transactional
  public void markUsed(Long benefitId, String invoiceId) {
    userBenefits
        .findById(benefitId)
        .orElseThrow(() -> new ResourceNotFoundException("特典が見つかりません"))
        .markUsed(LocalDateTime.now(), invoiceId);
  }

  /**
   * invoice.paidのWebhookから、そのSubscriptionに対して現在APPLIED状態の特典をUSEDに確定する。
   * 「APPLIED状態である」という条件だけに依存せず、UserBenefit.stripeSubscriptionIdが
   * 引数のsubscriptionIdと一致することを必須条件にする(クエリ側の絞り込みに加えて、
   * ここでも明示的に再確認する。古いAPPLIED特典が何らかの理由で残留していても、
   * 無関係な別Subscriptionのinvoice.paidによって誤ってUSEDにされないようにするため)。
   * 既にUSED(=同一invoice.paidの再送、または既に確定済み)の行はAPPLIED限定のクエリで
   * 自動的に除外されるため、ここでの呼び出しは何度実行しても安全(冪等)。
   */
  @Transactional
  public void markUsedBySubscription(String subscriptionId, String invoiceId) {
    for (var b : userBenefits.findByStripeSubscriptionIdAndStatus(subscriptionId, BenefitStatus.APPLIED)) {
      if (!subscriptionId.equals(b.getStripeSubscriptionId())) continue;
      b.markUsed(LocalDateTime.now(), invoiceId);
    }
  }

  /** 管理者による取消。ADMIN以外からの呼び出しは拒否する(SecurityConfigの/admin/**制御に加えたサービス層での二重チェック)。 */
  @Transactional
  public void revokeAsAdmin(User admin, Long benefitId) {
    if (admin.getRole() != Role.ADMIN) throw new ForbiddenOperationException("この操作には管理者権限が必要です");
    userBenefits
        .findById(benefitId)
        .orElseThrow(() -> new ResourceNotFoundException("特典が見つかりません"))
        .revoke(LocalDateTime.now());
  }

  private void syncStaleState(UserBenefit benefit, LocalDateTime now) {
    if (benefit.getStatus() == BenefitStatus.AVAILABLE && benefit.isExpired(now)) {
      benefit.expire();
      return;
    }
    if (benefit.getStatus() == BenefitStatus.RESERVED
        && benefit.getReservedAt() != null
        && Duration.between(benefit.getReservedAt(), now).compareTo(STALE_RESERVATION_TIMEOUT) > 0) {
      benefit.releaseReservation();
    }
  }
}
