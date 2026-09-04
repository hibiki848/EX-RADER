package com.exradar.repository;

import com.exradar.entity.AdminAnnouncementRecipient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAnnouncementRecipientRepository extends JpaRepository<AdminAnnouncementRecipient, Long> {
  /**
   * ログイン後モーダルに表示できる候補(このユーザー宛・永久非表示にしていない・公開中・配信期間内)を、
   * 管理者指定のpriority降順→新しいお知らせ順で取得する。NavigationAdviceがPageRequest.of(0, 1)を渡し、
   * 「一度に1件だけ表示」を実現する。announcementはJOIN FETCHし、モーダル描画に必要な情報を
   * 追加クエリなしで得られるようにする。
   */
  @Query(
      "select r from AdminAnnouncementRecipient r join fetch r.announcement a "
          + "where r.user.id = :userId and r.dismissedPermanentlyAt is null "
          + "and a.published = true and a.startsAt <= :now and (a.endsAt is null or a.endsAt > :now) "
          + "order by a.priority desc, a.startsAt desc")
  List<AdminAnnouncementRecipient> findDisplayCandidates(
      @Param("userId") Long userId, @Param("now") LocalDateTime now, Pageable pageable);

  /**
   * 他ユーザー宛の受信者IDをURLへ直打ちしても操作できないよう、userIdで必ず絞り込む
   * (運営メッセージのfindByIdAndUserIdと同じ考え方)。
   */
  Optional<AdminAnnouncementRecipient> findByIdAndUserId(Long id, Long userId);

  long countByAnnouncementId(Long announcementId);

  long countByAnnouncementIdAndFirstDisplayedAtIsNotNull(Long announcementId);

  long countByAnnouncementIdAndDismissedPermanentlyAtIsNotNull(Long announcementId);

  @Query(
      "select r from AdminAnnouncementRecipient r join fetch r.user where r.announcement.id = :announcementId "
          + "order by r.user.displayName")
  List<AdminAnnouncementRecipient> findByAnnouncementIdOrderByUserDisplayName(
      @Param("announcementId") Long announcementId);

  void deleteByUserId(Long userId);
}
