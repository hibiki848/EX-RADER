package com.exradar.service;

import com.exradar.dto.AdminAnnouncementHistoryDto;
import com.exradar.dto.AdminUserSearchCriteria;
import com.exradar.entity.AdminAnnouncement;
import com.exradar.entity.AdminAnnouncementRecipient;
import com.exradar.entity.User;
import com.exradar.exception.ForbiddenOperationException;
import com.exradar.exception.ResourceNotFoundException;
import com.exradar.repository.AdminAnnouncementRecipientRepository;
import com.exradar.repository.AdminAnnouncementRepository;
import com.exradar.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ログイン後モーダルで表示する「運営からのお知らせ」。既存の運営メッセージ
 * (AdminMessagingService、通知一覧に残るBOX型)とは役割を分離した独立機能だが、
 * ユーザー検索・セグメント抽出はAdminUserSearchServiceをそのまま再利用する
 * (検索ロジックの再実装はしない)。
 *
 * 対象は作成時点で固定: create()は呼ばれた瞬間に宛先ユーザーIDを確定させ、その場で
 * AdminAnnouncementRecipientレコードを作成する(検索条件そのものを保存して後から
 * 再評価する設計にはしない)。作成した時点ではpublished=falseのため、実際にユーザーへ
 * モーダル表示されるのはpublish()を呼んだ後、かつstartsAt〜endsAtの配信期間内のみ。
 * publish/unpublishは対象を再評価しない単純な公開状態の切り替えであり、
 * 内容編集(updateContent)も対象ユーザーの集合には影響しない。
 */
@Service
public class AdminAnnouncementService {
  public static final int LIST_PAGE_SIZE = 20;

  private final AdminAnnouncementRepository announcements;
  private final AdminAnnouncementRecipientRepository recipients;
  private final UserRepository users;
  private final AdminUserSearchService userSearch;

  public AdminAnnouncementService(
      AdminAnnouncementRepository announcements,
      AdminAnnouncementRecipientRepository recipients,
      UserRepository users,
      AdminUserSearchService userSearch) {
    this.announcements = announcements;
    this.recipients = recipients;
    this.users = users;
    this.userSearch = userSearch;
  }

  /** 現在の検索条件に一致する全ユーザーのIDを、作成前のプレビュー人数表示に使う。 */
  @Transactional(readOnly = true)
  public List<Long> resolveTargetIdsForCriteria(AdminUserSearchCriteria criteria) {
    return userSearch.findAllMatchingUserIds(criteria);
  }

  @Transactional
  public AdminAnnouncement create(
      String title,
      String body,
      String linkUrl,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      int priority,
      String adminEmail,
      List<Long> targetUserIds) {
    var distinctIds = new LinkedHashSet<>(targetUserIds);
    if (distinctIds.isEmpty()) throw new IllegalArgumentException("配信先が0人のため作成できません");
    if (endsAt != null && !endsAt.isAfter(startsAt))
      throw new IllegalArgumentException("配信終了日時は配信開始日時より後にしてください");

    var admin =
        users
            .findByEmailIgnoreCase(adminEmail)
            .orElseThrow(() -> new ForbiddenOperationException("管理者アカウントを確認できません"));
    var announcement =
        announcements.save(
            new AdminAnnouncement(title.trim(), body.trim(), blankToNull(linkUrl), admin, startsAt, endsAt, priority));

    // findAllByIdは存在しないID(既に退会した等)を自然に除外してくれるため、
    // 実際に作成する対象者数は指定したIDの数と一致しないことがある。
    for (var target : users.findAllById(distinctIds)) {
      recipients.save(new AdminAnnouncementRecipient(announcement, target));
    }
    return announcement;
  }

  /** 内容編集。対象ユーザーの集合(Recipient)には一切触れない(作成時点で固定済みのため)。 */
  @Transactional
  public void updateContent(
      Long id, String title, String body, String linkUrl, LocalDateTime startsAt, LocalDateTime endsAt, int priority) {
    if (endsAt != null && !endsAt.isAfter(startsAt))
      throw new IllegalArgumentException("配信終了日時は配信開始日時より後にしてください");
    var announcement =
        announcements.findById(id).orElseThrow(() -> new ResourceNotFoundException("お知らせが見つかりません"));
    announcement.updateContent(title.trim(), body.trim(), blankToNull(linkUrl), startsAt, endsAt, priority);
  }

  @Transactional
  public void publish(Long id) {
    announcements.findById(id).orElseThrow(() -> new ResourceNotFoundException("お知らせが見つかりません")).publish();
  }

  @Transactional
  public void unpublish(Long id) {
    announcements.findById(id).orElseThrow(() -> new ResourceNotFoundException("お知らせが見つかりません")).unpublish();
  }

  @Transactional(readOnly = true)
  public Page<AdminAnnouncementHistoryDto> history(int page) {
    var result = announcements.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0, page), LIST_PAGE_SIZE));
    return result.map(
        a ->
            new AdminAnnouncementHistoryDto(
                a.getId(),
                a.getTitle(),
                a.isPublished(),
                a.getCreatedByAdmin() != null ? a.getCreatedByAdmin().getDisplayName() : "(退会済み)",
                a.getStartsAt(),
                a.getEndsAt(),
                recipients.countByAnnouncementId(a.getId()),
                recipients.countByAnnouncementIdAndFirstDisplayedAtIsNotNull(a.getId()),
                recipients.countByAnnouncementIdAndDismissedPermanentlyAtIsNotNull(a.getId())));
  }

  @Transactional(readOnly = true)
  public AdminAnnouncement detail(Long id) {
    return announcements
        .findByIdWithCreatedByAdmin(id)
        .orElseThrow(() -> new ResourceNotFoundException("お知らせが見つかりません"));
  }

  @Transactional(readOnly = true)
  public List<AdminAnnouncementRecipient> recipientsOf(Long announcementId) {
    return recipients.findByAnnouncementIdOrderByUserDisplayName(announcementId);
  }

  // ---- ユーザー側(ログイン後モーダル) ----

  /**
   * ログイン後ページ表示のたび(NavigationAdvice、GETかつ同一セッション内で未表示の場合のみ)に
   * 呼ばれる。表示できるお知らせが無ければnull。見つかった場合はここで
   * firstDisplayedAt/lastDisplayedAt/displayCountを更新する
   * (=モーダルを実際にユーザーへ返す(モデルへ含める)時点で必ずDBへ記録される。
   * JavaScript側だけで表示したことにする設計にはしていない)。
   * 複数該当する場合はpriority降順→新しいお知らせ順の先頭1件のみを返す(一度に1件だけ表示)。
   */
  @Transactional
  public AdminAnnouncementRecipient pickAndRecordDisplayIfDue(String email) {
    var user = users.findByEmailIgnoreCase(email).orElse(null);
    if (user == null) return null;
    var now = LocalDateTime.now();
    var candidates = recipients.findDisplayCandidates(user.getId(), now, Pageable.ofSize(1));
    if (candidates.isEmpty()) return null;
    var recipient = candidates.get(0);
    recipient.recordDisplay(now);
    return recipient;
  }

  /**
   * 「次回以降このお知らせを表示しない」。recipient.userIdが現在のログインユーザーと
   * 一致しない場合は存在しないものとして扱う(他ユーザー宛の受信者IDをURLへ直打ちしても
   * 操作できない)。dismissPermanently自体が冪等なため、何度呼んでも状態はおかしくならない。
   */
  @Transactional
  public void dismissPermanently(Long recipientId, String email) {
    var user = currentUser(email);
    var recipient =
        recipients
            .findByIdAndUserId(recipientId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("お知らせが見つかりません"));
    recipient.dismissPermanently(LocalDateTime.now());
  }

  private User currentUser(String email) {
    return users.findByEmailIgnoreCase(email).orElseThrow(() -> new ForbiddenOperationException("ログインが必要です"));
  }

  private String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }
}
