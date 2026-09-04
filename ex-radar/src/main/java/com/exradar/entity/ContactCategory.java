package com.exradar.entity;

public enum ContactCategory {
  GENERAL,
  ACCOUNT,
  POST,
  REPORT,
  PREMIUM,
  BUG,
  PRIVACY,
  OTHER;

  public String displayName() {
    return switch (this) {
      case GENERAL -> "サービスについて";
      case ACCOUNT -> "アカウントについて";
      case POST -> "投稿について";
      case REPORT -> "不適切な投稿の報告";
      case PREMIUM -> "プレミアムプラン・料金について";
      case BUG -> "不具合について";
      case PRIVACY -> "個人情報について";
      case OTHER -> "その他";
    };
  }
}
