package com.exradar.service;

import com.exradar.dto.AdminMessageHistoryDto;
import com.exradar.dto.AdminUserSearchCriteria;
import com.exradar.dto.UserMessageSummaryDto;
import com.exradar.entity.AdminMessage;
import com.exradar.entity.AdminMessageRecipient;
import com.exradar.entity.User;
import com.exradar.exception.ForbiddenOperationException;
import com.exradar.exception.ResourceNotFoundException;
import com.exradar.repository.AdminMessageRecipientRepository;
import com.exradar.repository.AdminMessageRepository;
import com.exradar.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 運営から会員への一括/個別メッセージ配信。ユーザー検索・セグメント抽出そのものは
 * AdminUserSearchService(前回実装)をそのまま再利用し、ここでは「確定した宛先へ
 * メッセージを作成・配信する」責務のみを持つ(検索ロジックの再実装はしない)。
 *
 * 対象は送信時点で固定: sendToUsers/sendToSearchCriteriaはどちらも、呼ばれた瞬間に
 * 宛先ユーザーIDを確定させ、その場でAdminMessageRecipientレコードを作成する。
 * 検索条件そのものを保存して後から再評価する設計には*しない*(送信後にユーザーの
 * プラン・投稿数が変わっても、既に送ったメッセージの受信対象は変化しない)。
 */
@Service
public class AdminMessagingService {
  public static final int LIST_PAGE_SIZE = 20;

  private final AdminMessageRepository messages;
  private final AdminMessageRecipientRepository recipients;
  private final UserRepository users;
  private final AdminUserSearchService userSearch;

  public AdminMessagingService(
      AdminMessageRepository messages,
      AdminMessageRecipientRepository recipients,
      UserRepository users,
      AdminUserSearchService userSearch) {
    this.messages = messages;
    this.recipients = recipients;
    this.users = users;
    this.userSearch = userSearch;
  }

  /** 現在の検索条件に一致する全ユーザーのIDを、送信前のプレビュー人数表示に使う。 */
  @Transactional(readOnly = true)
  public List<Long> resolveTargetIdsForCriteria(AdminUserSearchCriteria criteria) {
    return userSearch.findAllMatchingUserIds(criteria);
  }

  @Transactional
  public AdminMessage send(String title, String body, String linkUrl, String adminEmail, List<Long> targetUserIds) {
    var distinctIds = new LinkedHashSet<>(targetUserIds);
    if (distinctIds.isEmpty()) throw new IllegalArgumentException("送信先が0人のため送信できません");

    var admin =
        users
            .findByEmailIgnoreCase(adminEmail)
            .orElseThrow(() -> new ForbiddenOperationException("管理者アカウントを確認できません"));
    var now = LocalDateTime.now();
    var message = messages.save(new AdminMessage(title.trim(), body.trim(), blankToNull(linkUrl), admin, now));

    // findAllByIdは存在しないID(既に退会した等)を自然に除外してくれるため、
    // 実際に作成する受信者数は指定したIDの数と一致しないことがある。
    for (var target : users.findAllById(distinctIds)) {
      recipients.save(new AdminMessageRecipient(message, target));
    }
    return message;
  }

  @Transactional(readOnly = true)
  public Page<AdminMessageHistoryDto> history(int page) {
    var result =
        messages.findAllByOrderBySentAtDesc(PageRequest.of(Math.max(0, page), LIST_PAGE_SIZE));
    return result.map(
        m ->
            new AdminMessageHistoryDto(
                m.getId(),
                m.getTitle(),
                m.getCreatedByAdmin() != null ? m.getCreatedByAdmin().getDisplayName() : "(退会済み)",
                m.getSentAt(),
                recipients.countByMessageId(m.getId()),
                recipients.countByMessageIdAndReadAtIsNotNull(m.getId()),
                recipients.countByMessageId(m.getId()) - recipients.countByMessageIdAndReadAtIsNotNull(m.getId())));
  }

  @Transactional(readOnly = true)
  public AdminMessage messageDetail(Long id) {
    return messages
        .findByIdWithCreatedByAdmin(id)
        .orElseThrow(() -> new ResourceNotFoundException("メッセージが見つかりません"));
  }

  @Transactional(readOnly = true)
  public List<AdminMessageRecipient> recipientsOf(Long messageId) {
    return recipients.findByMessageIdOrderByUserDisplayName(messageId);
  }

  // ---- ユーザー側 ----

  /** 匿名ユーザーでは呼ばない(呼び出し側であらかじめログイン判定すること)。 */
  @Transactional(readOnly = true)
  public long unreadCount(String email) {
    return users.findByEmailIgnoreCase(email).map(u -> recipients.countByUserIdAndReadAtIsNull(u.getId())).orElse(0L);
  }

  @Transactional(readOnly = true)
  public Page<UserMessageSummaryDto> listFor(String email, int page) {
    var user = currentUser(email);
    return recipients
        .findByUserIdOrderByMessageSentAtDesc(user.getId(), PageRequest.of(Math.max(0, page), LIST_PAGE_SIZE, Sort.unsorted()))
        .map(UserMessageSummaryDto::from);
  }

  /**
   * 通知を開く=既読にする。recipient.userIdが現在のログインユーザーと一致しない場合は
   * 存在しないものとして扱う(他ユーザー宛の通知IDをURLへ直打ちしても閲覧できない)。
   * markRead自体が冪等なため、同じ通知を何度開いても状態はおかしくならない。
   */
  @Transactional
  public AdminMessageRecipient open(Long recipientId, String email) {
    var user = currentUser(email);
    var recipient =
        recipients
            .findByIdAndUserId(recipientId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("通知が見つかりません"));
    recipient.markRead(LocalDateTime.now());
    return recipient;
  }

  private User currentUser(String email) {
    return users.findByEmailIgnoreCase(email).orElseThrow(() -> new ForbiddenOperationException("ログインが必要です"));
  }

  private String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }
}
