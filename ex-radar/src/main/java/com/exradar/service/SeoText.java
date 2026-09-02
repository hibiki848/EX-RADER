package com.exradar.service;

/**
 * meta description等に使う本文の安全な要約を作る。改行・連続空白を1つの半角スペースへ畳み込み、
 * HTMLタグらしき文字列は念のため除去したうえで、指定文字数で安全に切り詰める。
 */
public final class SeoText {
  private SeoText() {}

  public static String excerpt(String raw, int maxLength) {
    if (raw == null) return null;
    String plain = raw.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    if (plain.isEmpty()) return null;
    if (plain.length() <= maxLength) return plain;
    return plain.substring(0, maxLength).trim() + "…";
  }
}
