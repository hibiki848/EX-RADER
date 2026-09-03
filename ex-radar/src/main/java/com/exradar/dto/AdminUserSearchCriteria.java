package com.exradar.dto;

import com.exradar.entity.PlanType;
import com.exradar.entity.Role;
import java.time.LocalDate;
import java.util.Set;

/**
 * 管理者のユーザー検索・セグメント条件。管理者ユーザー一覧に限らず、今後の
 * 個別・一括メッセージ配信、ログイン時のお知らせ配信でも同じ条件定義を再利用するための
 * 独立したDTO(AdminUserSpecifications/AdminUserSearchServiceとセットで使う)。
 * 各フィールドはnull(またはplansが空集合)なら「指定なし=すべて」を意味し、
 * 複数フィールドを指定した場合はAND条件で組み合わされる。
 * role/suspendedは既存の管理者ユーザー一覧の「管理者のみ/停止中のみ」表示を
 * このDTOへ統合するために持たせている(役割・利用停止状態での絞り込みは
 * メッセージ配信・お知らせ配信でも自然に使うため)。
 */
public record AdminUserSearchCriteria(
    String name,
    String email,
    Long userId,
    Role role,
    Boolean suspended,
    Set<PlanType> plans,
    LocalDate registeredFrom,
    LocalDate registeredTo,
    Integer registeredDaysAgoMin,
    Integer registeredDaysAgoMax,
    LocalDate firstLoginFrom,
    LocalDate firstLoginTo,
    LocalDate lastLoginFrom,
    LocalDate lastLoginTo,
    Boolean neverLoggedIn,
    LocalDate firstPostFrom,
    LocalDate firstPostTo,
    Boolean hasPosted,
    Integer postCountMin,
    Integer postCountMax,
    Boolean everPaid,
    LocalDate firstPaidFrom,
    LocalDate firstPaidTo,
    Boolean currentlyPaid,
    Integer paidDurationMinDays,
    Integer paidDurationMaxDays) {

  public static AdminUserSearchCriteria empty() {
    return new AdminUserSearchCriteria(
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null);
  }

  /** 何も条件を指定していない(=全ユーザーが対象になる)かどうか。将来のメッセージ配信で「全ユーザー宛」を警告表示する等に使う。 */
  public boolean isEmpty() {
    return blank(name)
        && blank(email)
        && userId == null
        && role == null
        && suspended == null
        && (plans == null || plans.isEmpty())
        && registeredFrom == null
        && registeredTo == null
        && registeredDaysAgoMin == null
        && registeredDaysAgoMax == null
        && firstLoginFrom == null
        && firstLoginTo == null
        && lastLoginFrom == null
        && lastLoginTo == null
        && neverLoggedIn == null
        && firstPostFrom == null
        && firstPostTo == null
        && hasPosted == null
        && postCountMin == null
        && postCountMax == null
        && everPaid == null
        && firstPaidFrom == null
        && firstPaidTo == null
        && currentlyPaid == null
        && paidDurationMinDays == null
        && paidDurationMaxDays == null;
  }

  private static boolean blank(String v) {
    return v == null || v.isBlank();
  }
}
