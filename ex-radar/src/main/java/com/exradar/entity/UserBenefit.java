package com.exradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * ユーザーが実際に保有する特典。EXレーダー側を「保有特典の正」とし、Stripe側の状態だけに
 * 依存しない(Stripe連携の詳細はPremiumService/StripeServiceが担い、このエンティティは
 * 「今どういう状態か」という事実そのものを表す)。
 *
 * benefitName以下の*Snapshot系フィールドは付与した瞬間のBenefitDefinitionの内容を
 * コピーしたもの。付与後にマスタ側(割引率等)を変更しても、既に付与済みの特典の
 * 表示内容・Stripeへ適用する内容が勝手に変わらないようにするため。
 *
 * 状態遷移はAVAILABLE→RESERVED→APPLIED→USED、またはAVAILABLE/RESERVEDから
 * REVOKED(APPLIED以降は取消不可。Stripe側に既に設定済みの可能性があるため)、
 * AVAILABLEからEXPIREDのみを許可し、それ以外は例外にする(不正な遷移を
 * サービス層の呼び出し順序の誤りによって起こさせないため、遷移ルール自体をエンティティに閉じ込める)。
 */
@Entity
@Table(
    name = "user_benefits",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_user_benefits_reward_grant_key", columnNames = "reward_grant_key"))
public class UserBenefit extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User user;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private BenefitDefinition benefitDefinition;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private BenefitSourceType sourceType;

  @Column(name = "source_reference_id")
  private Long sourceReferenceId;

  @Column(name = "source_description", length = 300)
  private String sourceDescription;

  @Column(name = "reward_grant_key", length = 150)
  private String rewardGrantKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BenefitStatus status = BenefitStatus.AVAILABLE;

  @Column(name = "benefit_name_snapshot", nullable = false, length = 100)
  private String benefitNameSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "benefit_type_snapshot", nullable = false, length = 30)
  private BenefitType benefitTypeSnapshot;

  @Column(name = "discount_percent_snapshot")
  private Integer discountPercentSnapshot;

  @Column(name = "free_months_snapshot")
  private Integer freeMonthsSnapshot;

  @Column(name = "stripe_coupon_key_snapshot", nullable = false, length = 100)
  private String stripeCouponKeySnapshot;

  @Column(name = "granted_at", nullable = false)
  private LocalDateTime grantedAt;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  @Column(name = "reserved_at")
  private LocalDateTime reservedAt;

  @Column(name = "applied_at")
  private LocalDateTime appliedAt;

  @Column(name = "used_at")
  private LocalDateTime usedAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  @Column(name = "stripe_checkout_session_id")
  private String stripeCheckoutSessionId;

  @Column(name = "stripe_subscription_id")
  private String stripeSubscriptionId;

  @Column(name = "stripe_invoice_id")
  private String stripeInvoiceId;

  protected UserBenefit() {}

  public UserBenefit(
      User user,
      BenefitDefinition benefitDefinition,
      BenefitSourceType sourceType,
      Long sourceReferenceId,
      String sourceDescription,
      String rewardGrantKey,
      LocalDateTime grantedAt) {
    this.user = user;
    this.benefitDefinition = benefitDefinition;
    this.sourceType = sourceType;
    this.sourceReferenceId = sourceReferenceId;
    this.sourceDescription = sourceDescription;
    this.rewardGrantKey = rewardGrantKey;
    this.grantedAt = grantedAt;
    this.benefitNameSnapshot = benefitDefinition.getName();
    this.benefitTypeSnapshot = benefitDefinition.getBenefitType();
    this.discountPercentSnapshot = benefitDefinition.getDiscountPercent();
    this.freeMonthsSnapshot = benefitDefinition.getFreeMonths();
    this.stripeCouponKeySnapshot = benefitDefinition.getStripeCouponKey();
    this.expiresAt =
        benefitDefinition.getExpiresDays() == null
            ? null
            : grantedAt.plusDays(benefitDefinition.getExpiresDays());
  }

  /** 使用開始(Stripe処理開始)。AVAILABLEからのみ許可。期限切れの場合は先にEXPIREDへ遷移させてから拒否する。 */
  public void reserve(LocalDateTime at) {
    if (status == BenefitStatus.AVAILABLE && expiresAt != null && !expiresAt.isAfter(at)) {
      expire();
    }
    if (status != BenefitStatus.AVAILABLE)
      throw new IllegalStateException("この特典は現在使用できません(状態: " + status + ")");
    status = BenefitStatus.RESERVED;
    reservedAt = at;
  }

  /**
   * Checkout Session作成直後、まだRESERVEDのままセッションIDだけを記録する(状態は変えない)。
   * Webhook側がstripe_checkout_session_idからこのUserBenefitを逆引きできるようにするため
   * (成功したことの確定はmarkApplied/markUsedで別途行う)。
   */
  public void attachCheckoutSession(String checkoutSessionId) {
    this.stripeCheckoutSessionId = checkoutSessionId;
  }

  /** Stripe処理を諦めた・失敗した場合にAVAILABLEへ戻す(消費済みにはしない)。RESERVEDからのみ許可。 */
  public void releaseReservation() {
    if (status != BenefitStatus.RESERVED)
      throw new IllegalStateException("予約状態ではない特典を解放できません(状態: " + status + ")");
    status = BenefitStatus.AVAILABLE;
    reservedAt = null;
  }

  /**
   * checkout.session.expired等の冪等なWebhook処理向け。RESERVED以外は何もしない(例外を投げない)。
   * Webhookは到着順・再送に依存できないため、既にAPPLIED/USED/REVOKED等へ進んでいる場合に
   * 誤って逆戻りさせないよう、releaseReservation()とは別にこの安全な変種を用意する。
   */
  public void releaseReservationIfStillReserved() {
    if (status == BenefitStatus.RESERVED) {
      status = BenefitStatus.AVAILABLE;
      reservedAt = null;
    }
  }

  /** Stripe側への割引設定が正常に完了した(まだ請求成功は未確定)。RESERVEDからのみ許可。 */
  public void markApplied(
      LocalDateTime at, String checkoutSessionId, String subscriptionId) {
    if (status != BenefitStatus.RESERVED)
      throw new IllegalStateException("予約状態ではない特典をAPPLIEDにできません(状態: " + status + ")");
    status = BenefitStatus.APPLIED;
    appliedAt = at;
    if (checkoutSessionId != null) this.stripeCheckoutSessionId = checkoutSessionId;
    if (subscriptionId != null) this.stripeSubscriptionId = subscriptionId;
  }

  /** 実際の請求成功が確定した(使用済み確定)。APPLIEDからのみ許可。 */
  public void markUsed(LocalDateTime at, String invoiceId) {
    if (status != BenefitStatus.APPLIED)
      throw new IllegalStateException("APPLIED状態ではない特典をUSEDにできません(状態: " + status + ")");
    status = BenefitStatus.USED;
    usedAt = at;
    if (invoiceId != null) this.stripeInvoiceId = invoiceId;
  }

  /**
   * 管理者による取消。AVAILABLE・RESERVEDのみ取消可能。APPLIEDは既にStripe側へ割引設定済みの
   * 可能性があり、EXレーダーDBだけREVOKEDにするとStripeとの状態不整合が生まれるため、
   * 今回は対象外とする(Stripe側のDiscount取消を伴う機能は将来の別実装とする)。
   * USED・EXPIRED・既にREVOKED済みも同様に取り消せない。
   */
  public void revoke(LocalDateTime at) {
    if (status != BenefitStatus.AVAILABLE && status != BenefitStatus.RESERVED)
      throw new IllegalStateException("この状態の特典は取り消せません(状態: " + status + ")");
    status = BenefitStatus.REVOKED;
    revokedAt = at;
  }

  /** 有効期限切れへの遷移。AVAILABLEからのみ意味を持つ(他の状態は別の理由で既に確定しているため対象外)。 */
  public void expire() {
    if (status != BenefitStatus.AVAILABLE) return;
    status = BenefitStatus.EXPIRED;
  }

  public boolean isExpired(LocalDateTime at) {
    return expiresAt != null && !expiresAt.isAfter(at);
  }

  public User getUser() {
    return user;
  }

  public BenefitDefinition getBenefitDefinition() {
    return benefitDefinition;
  }

  public BenefitSourceType getSourceType() {
    return sourceType;
  }

  public Long getSourceReferenceId() {
    return sourceReferenceId;
  }

  public String getSourceDescription() {
    return sourceDescription;
  }

  public String getRewardGrantKey() {
    return rewardGrantKey;
  }

  public BenefitStatus getStatus() {
    return status;
  }

  public String getBenefitNameSnapshot() {
    return benefitNameSnapshot;
  }

  public BenefitType getBenefitTypeSnapshot() {
    return benefitTypeSnapshot;
  }

  public Integer getDiscountPercentSnapshot() {
    return discountPercentSnapshot;
  }

  public Integer getFreeMonthsSnapshot() {
    return freeMonthsSnapshot;
  }

  public String getStripeCouponKeySnapshot() {
    return stripeCouponKeySnapshot;
  }

  public LocalDateTime getGrantedAt() {
    return grantedAt;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public LocalDateTime getReservedAt() {
    return reservedAt;
  }

  public LocalDateTime getAppliedAt() {
    return appliedAt;
  }

  public LocalDateTime getUsedAt() {
    return usedAt;
  }

  public LocalDateTime getRevokedAt() {
    return revokedAt;
  }

  public String getStripeCheckoutSessionId() {
    return stripeCheckoutSessionId;
  }

  public String getStripeSubscriptionId() {
    return stripeSubscriptionId;
  }

  public String getStripeInvoiceId() {
    return stripeInvoiceId;
  }
}
