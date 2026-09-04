package com.exradar.repository;

import com.exradar.entity.AdminMessage;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminMessageRepository extends JpaRepository<AdminMessage, Long> {
  Page<AdminMessage> findAllByOrderBySentAtDesc(Pageable pageable);

  /**
   * createdByAdminはLAZYのため、詳細画面(admin/messages/detail.html)でトランザクション外から
   * message.createdByAdmin.displayNameへアクセスするとLazyInitializationExceptionになる。
   * 詳細表示専用にJOIN FETCHで一緒に取得する。
   */
  @Query("select m from AdminMessage m left join fetch m.createdByAdmin where m.id = :id")
  Optional<AdminMessage> findByIdWithCreatedByAdmin(@Param("id") Long id);

  /**
   * 送信した管理者が退会した際に、メッセージ本体・他ユーザーの受信記録は残したまま送信者情報だけ外す。
   * clearAutomatically=trueが必須: 一括UPDATE(JPQL)は永続コンテキストの1次キャッシュを
   * 経由しないため、これを付けないと同一トランザクション内で既にロード済みのAdminMessage
   * エンティティがDB上の変更を反映しない(古いcreatedByAdminを参照し続ける)まま返ってきてしまう。
   * flushAutomatically=trueも必須: これが無いと、AccountService#deleteAccount内でこれより前に
   * 呼んだ他の削除操作(deleteByUserId等、未フラッシュのまま永続コンテキストに滞留し得る)が
   * clear()によってDBへ反映されないまま消えてしまう(実際にAccountDeletionTestで検出・修正した不具合)。
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update AdminMessage m set m.createdByAdmin = null where m.createdByAdmin.id = :adminId")
  void clearCreatedByAdmin(@Param("adminId") Long adminId);
}
