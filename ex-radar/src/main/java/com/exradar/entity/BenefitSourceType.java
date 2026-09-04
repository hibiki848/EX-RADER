package com.exradar.entity;

/** UserBenefitがどのような経緯で付与されたか。将来の管理者手動付与・キャンペーン報酬等へ拡張できるようにしておく。 */
public enum BenefitSourceType {
  /** 体験談投稿数のマイルストーン達成による自動付与。 */
  POST_MILESTONE
}
