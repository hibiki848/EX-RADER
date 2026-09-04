package com.exradar.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public class AdminAnnouncementForm {
  @NotBlank(message = "タイトルを入力してください")
  @Size(max = 200, message = "タイトルは200文字以内で入力してください")
  private String title;

  @NotBlank(message = "本文を入力してください")
  @Size(max = 4000, message = "本文は4000文字以内で入力してください")
  private String body;

  @Size(max = 500, message = "リンクは500文字以内で入力してください")
  @Pattern(regexp = LinkUrlPattern.REGEX, message = LinkUrlPattern.MESSAGE)
  private String linkUrl;

  @NotNull(message = "配信開始日時を入力してください")
  @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
  private LocalDateTime startsAt;

  @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
  private LocalDateTime endsAt;

  private Integer priority = 0;

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

  public LocalDateTime getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(LocalDateTime v) {
    startsAt = v;
  }

  public LocalDateTime getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(LocalDateTime v) {
    endsAt = v;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer v) {
    priority = v;
  }
}
