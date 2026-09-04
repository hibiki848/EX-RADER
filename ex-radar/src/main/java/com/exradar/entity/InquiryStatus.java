package com.exradar.entity;

public enum InquiryStatus {
  NEW,
  IN_PROGRESS,
  RESOLVED,
  CLOSED;

  public String displayName() {
    return switch (this) {
      case NEW -> "未対応";
      case IN_PROGRESS -> "対応中";
      case RESOLVED -> "解決済み";
      case CLOSED -> "クローズ";
    };
  }
}
