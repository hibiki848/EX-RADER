package com.exradar.entity;

/**
 * 特典の種類。将来、プレミアム以外の特典(機能解放・AI利用回数付与・キャンペーン報酬等)を
 * 追加できるよう、特典の仕組み自体をプレミアム割引専用にしないための列挙型。
 * 現時点ではプレミアムに関する2種類のみ実装する。
 */
public enum BenefitType {
  /** プレミアム料金の割引(discountPercentを使用)。 */
  PREMIUM_DISCOUNT,
  /** プレミアム次回請求の無料化(freeMonthsを使用、現状は常に1)。 */
  PREMIUM_FREE_MONTH
}
