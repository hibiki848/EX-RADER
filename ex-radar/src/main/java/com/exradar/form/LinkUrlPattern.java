package com.exradar.form;

/**
 * 運営メッセージ(AdminMessageForm)・ログイン時お知らせ(AdminAnnouncementForm)で共通の
 * リンクURL検証パターン。空欄、http(s)から始まる絶対URL、またはEXレーダー内部の
 * 相対パス(先頭が"/"で始まり"//"では始まらない = プロトコル相対URLは弾く)のみ許可する。
 * javascript:等の危険なスキームは正規表現に一致しないため、そのまま保存・実行されない。
 */
public final class LinkUrlPattern {
  private LinkUrlPattern() {}

  public static final String REGEX = "^$|^https?://\\S+$|^/(?!/)\\S*$";
  public static final String MESSAGE = "リンクはhttp(s)から始まるURL、または/から始まるサイト内パスのみ指定できます";
}
