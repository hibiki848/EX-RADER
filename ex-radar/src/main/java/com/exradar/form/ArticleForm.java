package com.exradar.form;

import com.exradar.entity.Article;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ArticleForm {
  @NotBlank(message = "タイトルを入力してください")
  @Size(max = 150, message = "タイトルは150文字以内で入力してください")
  private String title;

  @NotBlank(message = "URL(スラッグ)を入力してください")
  @Size(max = 80, message = "URL(スラッグ)は80文字以内で入力してください")
  @Pattern(
      regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
      message = "URL(スラッグ)は半角小文字英数字とハイフンのみ使用できます(例: tenshoku-koukai)")
  private String slug;

  @NotBlank(message = "概要(meta descriptionにも使用)を入力してください")
  @Size(max = 300, message = "概要は300文字以内で入力してください")
  private String description;

  @NotBlank(message = "本文を入力してください")
  @Size(max = 20000, message = "本文は20000文字以内で入力してください")
  private String content;

  public static ArticleForm from(Article article) {
    var form = new ArticleForm();
    form.title = article.getTitle();
    form.slug = article.getSlug();
    form.description = article.getDescription();
    form.content = article.getContent();
    return form;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String v) {
    slug = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String v) {
    content = v;
  }
}
