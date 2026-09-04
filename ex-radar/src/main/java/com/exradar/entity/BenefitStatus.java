package com.exradar.entity;

/**
 * UserBenefitの状態。状態遷移はUserBenefit自身のメソッドでのみ許可し、
 * 不正な遷移(例: USEDから他状態への変更)は例外にする。
 */
public enum BenefitStatus {
  /** 使用可能。 */
  AVAILABLE,
  /** ユーザーが使用操作を開始し、Stripe処理が進行中。 */
  RESERVED,
  /** Stripe側への割引設定は正常に完了したが、実際の請求成功はまだ確定していない。 */
  APPLIED,
  /** 実際の請求への利用が確定した(使用済み、再利用不可)。 */
  USED,
  /** 管理者等によって取り消された。 */
  REVOKED,
  /** 有効期限切れ。 */
  EXPIRED
}
