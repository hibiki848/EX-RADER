package com.exradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 特典マスタ。Stripe Couponの実IDは環境(テスト/本番)ごとに異なるため保持せず、
 * stripeCouponKey(application.ymlや環境変数で実IDへ解決するための論理キー)のみを持つ。
 * このマスタの内容を変更しても、既に付与済みのUserBenefitはgrant時点のスナップショットを
 * 持つため内容が勝手に変わらない(UserBenefit参照)。
 */
@Entity
@Table(
    name = "benefit_definitions",
    uniqueConstraints = @UniqueConstraint(name = "uk_benefit_definitions_code", columnNames = "code"))
public class BenefitDefinition extends BaseEntity {
  @Column(nullable = false, length = 50)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "benefit_type", nullable = false, length = 30)
  private BenefitType benefitType;

  @Column(name = "discount_percent")
  private Integer discountPercent;

  @Column(name = "free_months")
  private Integer freeMonths;

  @Column(length = 500)
  private String description;

  @Column(name = "expires_days")
  private Integer expiresDays;

  @Column(name = "stripe_coupon_key", nullable = false, length = 100)
  private String stripeCouponKey;

  @Column(nullable = false)
  private boolean active = true;

  protected BenefitDefinition() {}

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public BenefitType getBenefitType() {
    return benefitType;
  }

  public Integer getDiscountPercent() {
    return discountPercent;
  }

  public Integer getFreeMonths() {
    return freeMonths;
  }

  public String getDescription() {
    return description;
  }

  public Integer getExpiresDays() {
    return expiresDays;
  }

  public String getStripeCouponKey() {
    return stripeCouponKey;
  }

  public boolean isActive() {
    return active;
  }
}
