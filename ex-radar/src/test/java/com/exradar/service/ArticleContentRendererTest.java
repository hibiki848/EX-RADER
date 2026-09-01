package com.exradar.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArticleContentRendererTest {
  private final ArticleContentRenderer renderer = new ArticleContentRenderer();

  @Test
  void rendersHeadingParagraphListAndBold() {
    String content =
        "## 見出し\n" + "段落の文章です。\n" + "\n" + "- 箇条書き1\n" + "- 箇条書き2\n" + "\n" + "**太字**を含む段落。";

    String html = renderer.render(content);

    assertThat(html).contains("<h2>見出し</h2>");
    assertThat(html).contains("<p>段落の文章です。</p>");
    assertThat(html).contains("<ul><li>箇条書き1</li><li>箇条書き2</li></ul>");
    assertThat(html).contains("<p><strong>太字</strong>を含む段落。</p>");
  }

  @Test
  void escapesHtmlToPreventXss() {
    String html = renderer.render("<script>alert('xss')</script>");

    assertThat(html).doesNotContain("<script>");
    assertThat(html).contains("&lt;script&gt;");
  }

  @Test
  void escapesHtmlEvenInsideHeadingAndListItems() {
    String html = renderer.render("## <img src=x onerror=alert(1)>\n- <b>not bold</b>");

    assertThat(html).doesNotContain("<img");
    assertThat(html).contains("&lt;img");
    assertThat(html).doesNotContain("<b>not bold</b>");
  }

  @Test
  void blankContentRendersEmptyString() {
    assertThat(renderer.render(null)).isEmpty();
    assertThat(renderer.render("   ")).isEmpty();
  }
}
