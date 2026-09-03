package com.exradar.repository;

import com.exradar.entity.AdminMessageRecipient;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminMessageRecipientRepository extends JpaRepository<AdminMessageRecipient, Long> {
  /**
   * 未読件数のみを取得する(モバイル下部ナビのバッジ用)。毎ページ表示されるため、
   * メッセージ本文等を一切ロードしないCOUNTクエリのみで済ませる。
   */
  long countByUserIdAndReadAtIsNull(Long userId);

  @Query(
      "select r from AdminMessageRecipient r join fetch r.message where r.user.id = :userId order by r.message.sentAt desc, r.id desc")
  Page<AdminMessageRecipient> findByUserIdOrderByMessageSentAtDesc(
      @Param("userId") Long userId, Pageable pageable);

  /** 通知詳細を開く際のURL直打ち対策。recipient.userIdが一致する場合のみ取得できる。 */
  @Query("select r from AdminMessageRecipient r join fetch r.message where r.id = :id and r.user.id = :userId")
  Optional<AdminMessageRecipient> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

  long countByMessageId(Long messageId);

  long countByMessageIdAndReadAtIsNotNull(Long messageId);

  @Query("select r from AdminMessageRecipient r join fetch r.user where r.message.id = :messageId order by r.user.displayName")
  java.util.List<AdminMessageRecipient> findByMessageIdOrderByUserDisplayName(
      @Param("messageId") Long messageId);

  void deleteByUserId(Long userId);
}
