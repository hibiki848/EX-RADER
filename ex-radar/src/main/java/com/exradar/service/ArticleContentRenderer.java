package com.exradar.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * 管理者が入力した記事本文(簡易マークアップ)を安全なHTMLへ変換する。
 * 対応する記法は見出し(## )・箇条書き(- )・太字(**text**)・段落(空行区切り)のみ。
 * 入力は必ず先にHTMLエスケープしてからタグを組み立てるため、任意のHTMLタグの混入(XSS)は発生しない。
 */
@Service
public class ArticleContentRenderer {
  private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");

  public String render(String content) {
    if (content == null || content.isBlank()) return "";
    String[] lines = content.replace("\r\n", "\n").split("\n", -1);

    StringBuilder html = new StringBuilder();
    List<String> paragraphLines = new ArrayList<>();
    List<String> listItems = new ArrayList<>();

    for (String rawLine : lines) {
      String line = rawLine.trim();
      if (line.isEmpty()) {
        flushParagraph(html, paragraphLines);
        flushList(html, listItems);
      } else if (line.startsWith("## ")) {
        flushParagraph(html, paragraphLines);
        flushList(html, listItems);
        html.append("<h2>").append(inline(line.substring(3).trim())).append("</h2>");
      } else if (line.startsWith("- ")) {
        flushParagraph(html, paragraphLines);
        listItems.add(inline(line.substring(2).trim()));
      } else {
        flushList(html, listItems);
        paragraphLines.add(inline(line));
      }
    }
    flushParagraph(html, paragraphLines);
    flushList(html, listItems);
    return html.toString();
  }

  private void flushParagraph(StringBuilder html, List<String> paragraphLines) {
    if (paragraphLines.isEmpty()) return;
    html.append("<p>").append(String.join("<br>", paragraphLines)).append("</p>");
    paragraphLines.clear();
  }

  private void flushList(StringBuilder html, List<String> listItems) {
    if (listItems.isEmpty()) return;
    html.append("<ul>");
    for (String item : listItems) html.append("<li>").append(item).append("</li>");
    html.append("</ul>");
    listItems.clear();
  }

  /** HTMLエスケープしたうえで**太字**だけを<strong>へ変換する。 */
  private String inline(String text) {
    String escaped = HtmlUtils.htmlEscape(text);
    Matcher matcher = BOLD.matcher(escaped);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(
          result, Matcher.quoteReplacement("<strong>" + matcher.group(1) + "</strong>"));
    }
    matcher.appendTail(result);
    return result.toString();
  }
}
