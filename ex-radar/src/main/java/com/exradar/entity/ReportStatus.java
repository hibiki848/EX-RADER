package com.exradar.entity;

public enum ReportStatus {
  PENDING,
  REVIEWING,
  NO_ACTION,
  HIDDEN,
  DELETED;

  public String displayName() {
    return switch (this) {
      case PENDING -> "未対応";
      case REVIEWING -> "確認中";
      case NO_ACTION -> "問題なし";
      case HIDDEN -> "非公開対応";
      case DELETED -> "削除対応";
    };
  }
}
