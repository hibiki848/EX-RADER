package com.exradar.form;

import jakarta.validation.constraints.*;

public class CommentForm {
  @NotBlank(message = "コメントを入力してください")
  @Size(max = 2000, message = "コメントは2000文字以内で入力してください")
  private String body;

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }
}
