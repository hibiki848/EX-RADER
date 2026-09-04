package com.exradar.repository;

import com.exradar.entity.AdminAnnouncement;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAnnouncementRepository extends JpaRepository<AdminAnnouncement, Long> {
  Page<AdminAnnouncement> findAllByOrderByCreatedAtDesc(Pageable pageable);

  /**
   * createdByAdminはLAZYのため、詳細画面でトランザクション外から
   * announcement.createdByAdmin.displayNameへアクセスするとLazyInitializationExceptionになる
   * (運営メッセージ機能で実際に発生・修正した不具合と同種)。詳細表示専用にJOIN FETCHで取得する。
   */
  @Query("select a from AdminAnnouncement a left join fetch a.createdByAdmin where a.id = :id")
  Optional<AdminAnnouncement> findByIdWithCreatedByAdmin(@Param("id") Long id);

  /**
   * 送信した管理者が退会した際に、お知らせ本体・他ユーザーの表示記録は残したまま作成者情報だけ外す。
   * clearAutomatically=trueが必須: 一括UPDATE(JPQL)は永続コンテキストの1次キャッシュを
   * 経由しないため、これを付けないと同一トランザクション内で既にロード済みの
   * AdminAnnouncementエンティティがDB上の変更を反映しない(古いcreatedByAdminを
   * 参照し続ける)まま返ってきてしまう。flushAutomatically=trueも必須: これが無いと、
   * AccountService#deleteAccount内でこれより前に呼んだ他の削除操作(deleteByUserId等、
   * 未フラッシュのまま永続コンテキストに滞留し得る)がclear()によってDBへ反映されないまま
   * 消えてしまう(実際にAccountDeletionTestで検出・修正した不具合)。
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update AdminAnnouncement a set a.createdByAdmin = null where a.createdByAdmin.id = :adminId")
  void clearCreatedByAdmin(@Param("adminId") Long adminId);
}
