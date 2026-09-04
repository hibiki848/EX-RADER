package com.exradar.form;

import com.exradar.entity.ContactCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ContactInquiryForm {
  @NotNull(message = "お問い合わせ種別を選択してください")
  private ContactCategory category;

  @Size(max = 100, message = "お名前は100文字以内で入力してください")
  private String name;

  @NotBlank(message = "メールアドレスを入力してください")
  @Email(message = "メールアドレスの形式が正しくありません")
  @Size(max = 254)
  private String email;

  @NotBlank(message = "件名を入力してください")
  @Size(max = 100, message = "件名は100文字以内で入力してください")
  private String subject;

  @NotBlank(message = "お問い合わせ内容を入力してください")
  @Size(max = 3000, message = "お問い合わせ内容は3000文字以内で入力してください")
  private String body;

  /** 不適切な投稿の報告等で、関連する体験談を紐付けたい場合に指定する(任意)。存在確認はサーバー側で行う。 */
  private Long relatedPostId;

  public ContactCategory getCategory() {
    return category;
  }

  public void setCategory(ContactCategory v) {
    category = v;
  }

  public String getName() {
    return name;
  }

  public void setName(String v) {
    name = v;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String v) {
    email = v;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String v) {
    subject = v;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String v) {
    body = v;
  }

  public Long getRelatedPostId() {
    return relatedPostId;
  }

  public void setRelatedPostId(Long v) {
    relatedPostId = v;
  }
}
