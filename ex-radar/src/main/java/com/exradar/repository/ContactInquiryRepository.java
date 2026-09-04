package com.exradar.repository;

import com.exradar.entity.ContactInquiry;
import com.exradar.entity.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactInquiryRepository extends JpaRepository<ContactInquiry, Long> {
  Page<ContactInquiry> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Page<ContactInquiry> findAllByStatusOrderByCreatedAtDesc(InquiryStatus status, Pageable pageable);

  long countByStatus(InquiryStatus status);

  /**
   * user/relatedPostはLAZYのため、詳細画面でトランザクション外からアクセスすると
   * LazyInitializationExceptionになる(運営メッセージ機能で実際に発生・修正した不具合と同種)。
   * 詳細表示専用にJOIN FETCHで一緒に取得する。
   */
  @Query("select i from ContactInquiry i left join fetch i.user left join fetch i.relatedPost where i.id = :id")
  java.util.Optional<ContactInquiry> findByIdWithAssociations(@Param("id") Long id);

  /**
   * 退会したユーザーが送信した問い合わせは、name/email/subject/body等の内容そのものは
   * サポート履歴として残す(AdminMessage.createdByAdminと同じ考え方)。userだけをNULLにし、
   * FK制約違反を避ける。clearAutomatically/flushAutomatically両方が必須な理由は
   * AdminMessageRepository#clearCreatedByAdminと同じ(一括UPDATEは1次キャッシュを経由しないため、
   * かつこれより前の削除操作の未フラッシュ分を消してしまわないため)。
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update ContactInquiry i set i.user = null where i.user.id = :userId")
  void clearUser(@Param("userId") Long userId);

  /** 関連付けた投稿(体験談)自体が削除される場合も同様にFK制約違反を避ける(問い合わせ内容は残す)。 */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update ContactInquiry i set i.relatedPost = null where i.relatedPost.id = :postId")
  void clearRelatedPost(@Param("postId") Long postId);
}
