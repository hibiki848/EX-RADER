package com.exradar.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AdminMessageForm {
  @NotBlank(message = "タイトルを入力してください")
  @Size(max = 200, message = "タイトルは200文字以内で入力してください")
  private String title;

  @NotBlank(message = "本文を入力してください")
  @Size(max = 4000, message = "本文は4000文字以内で入力してください")
  private String body;

  // 空欄は許容するが、指定する場合はhttp(s)のみ許可する(javascript:等の危険なスキームを保存させない)。
  @Size(max = 500, message = "リンクは500文字以内で入力してください")
  @Pattern(regexp = "^$|^https?://.+", message = "リンクはhttp(s)から始まるURLのみ指定できます")
  private String linkUrl;

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String v) {
    body = v;
  }

  public String getLinkUrl() {
    return linkUrl;
  }

  public void setLinkUrl(String v) {
    linkUrl = v;
  }
}
