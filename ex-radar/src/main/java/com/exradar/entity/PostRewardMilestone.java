package com.exradar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 投稿数と特典の対応(管理者が/admin/reward-milestonesから編集できる)。
 * repeatIntervalが設定されている行はrequiredPostCountを起点に無限に繰り返し対象になる
 * (例: requiredPostCount=10, repeatInterval=10 → 10,20,30,40...を全て対象とみなす)。
 * nullの場合はrequiredPostCountちょうどの時だけの1回きりの特典。
 * 実際にどの閾値をどこまで展開するかの判定はRewardService側で行う(DB駆動、
 * Javaコードへ具体的な投稿数をハードコードしない)。
 */
@Entity
@Table(
    name = "post_reward_milestones",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_post_reward_milestones_required_count",
            columnNames = "required_post_count"))
public class PostRewardMilestone extends BaseEntity {
  @Column(name = "required_post_count", nullable = false)
  private int requiredPostCount;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private BenefitDefinition benefitDefinition;

  @Column(name = "repeat_interval")
  private Integer repeatInterval;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  protected PostRewardMilestone() {}

  public PostRewardMilestone(
      int requiredPostCount, BenefitDefinition benefitDefinition, Integer repeatInterval, int displayOrder) {
    this.requiredPostCount = requiredPostCount;
    this.benefitDefinition = benefitDefinition;
    this.repeatInterval = repeatInterval;
    this.displayOrder = displayOrder;
  }

  public void update(
      int requiredPostCount, BenefitDefinition benefitDefinition, Integer repeatInterval, int displayOrder) {
    this.requiredPostCount = requiredPostCount;
    this.benefitDefinition = benefitDefinition;
    this.repeatInterval = repeatInterval;
    this.displayOrder = displayOrder;
  }

  public void activate() {
    active = true;
  }

  public void deactivate() {
    active = false;
  }

  public int getRequiredPostCount() {
    return requiredPostCount;
  }

  public BenefitDefinition getBenefitDefinition() {
    return benefitDefinition;
  }

  public Integer getRepeatInterval() {
    return repeatInterval;
  }

  public boolean isActive() {
    return active;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  /** thresholdがこのマイルストーンの対象閾値かどうか(繰り返しルールを考慮)。 */
  public boolean matches(int threshold) {
    if (threshold < requiredPostCount) return false;
    if (repeatInterval == null || repeatInterval <= 0) return threshold == requiredPostCount;
    return (threshold - requiredPostCount) % repeatInterval == 0;
  }
}
