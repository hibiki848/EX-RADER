package com.exradar.dto;

import com.exradar.entity.AdminMessageRecipient;
import java.time.LocalDateTime;

/** ログインユーザー本人の通知一覧(運営メッセージ)の1行分。 */
public record UserMessageSummaryDto(
    Long recipientId, String title, String bodyExcerpt, LocalDateTime sentAt, boolean read) {
  public static UserMessageSummaryDto from(AdminMessageRecipient r) {
    var body = r.getMessage().getBody();
    var excerpt = body.length() > 80 ? body.substring(0, 80) + "…" : body;
    return new UserMessageSummaryDto(
        r.getId(), r.getMessage().getTitle(), excerpt, r.getMessage().getSentAt(), r.isRead());
  }
}
