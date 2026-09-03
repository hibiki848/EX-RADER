package com.exradar.dto;

/** 管理者ユーザー検索の並び替え対象フィールド。 */
public enum AdminUserSortField {
  REGISTERED_AT,
  FIRST_LOGIN_AT,
  LAST_LOGIN_AT,
  FIRST_POST_AT,
  LAST_POST_AT,
  POST_COUNT,
  FIRST_PAID_AT,
  PAID_DURATION_DAYS
}
