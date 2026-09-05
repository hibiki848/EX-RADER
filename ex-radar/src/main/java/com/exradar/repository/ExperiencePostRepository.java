package com.exradar.repository;

import com.exradar.entity.ExperiencePost;
import com.exradar.entity.PostStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

public interface ExperiencePostRepository
    extends JpaRepository<ExperiencePost, Long>, JpaSpecificationExecutor<ExperiencePost> {
  // カテゴリは下書きでは未選択(null)のことがあるため、通常のjoin fetchだと除外されてしまう。
  // left join fetchに変更し、カテゴリ未選択の下書きも取得できるようにする。
  @Query(
      "select distinct p from ExperiencePost p join fetch p.author left join fetch p.category left join fetch p.lifeEvents left join fetch p.tags left join fetch p.values where p.id=:id")
  Optional<ExperiencePost> findDetailedById(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from ExperiencePost p join fetch p.author where p.id=:id")
  Optional<ExperiencePost> findForInteraction(@Param("id") Long id);

  @Override
  @EntityGraph(attributePaths = {"author", "category", "tags"})
  Page<ExperiencePost> findAll(Specification<ExperiencePost> specification, Pageable pageable);

  @EntityGraph(attributePaths = {"author", "category", "tags"})
  List<ExperiencePost> findTop6ByStatusOrderByCreatedAtDesc(PostStatus status);

  @EntityGraph(attributePaths = {"author", "category", "tags"})
  List<ExperiencePost> findTop6ByStatusOrderBySatisfactionDescCreatedAtDesc(PostStatus status);

  @EntityGraph(attributePaths = {"author", "category", "tags"})
  Page<ExperiencePost> findByAuthorIdAndStatus(Long authorId, PostStatus status, Pageable pageable);

  @EntityGraph(attributePaths = {"category", "tags"})
  List<ExperiencePost> findByAuthorIdAndStatus(Long authorId, PostStatus status);

  @EntityGraph(attributePaths = {"category"})
  List<ExperiencePost> findByAuthorIdAndStatusOrderByUpdatedAtDesc(Long authorId, PostStatus status);

  boolean existsByAuthorIdAndStatus(Long authorId, PostStatus status);

  /** 投稿報酬(RewardService)の特典対象投稿数の判定に使う。DRAFTには「未公開」「管理者による非公開対応」両方が含まれるため、これらは自動的にカウント対象から除外される。 */
  long countByAuthorIdAndStatus(Long authorId, PostStatus status);

  /**
   * 投稿報酬の特典対象投稿数。単にPUBLISHEDであるだけでなく、教訓(lesson)が投稿フォームと
   * 同じ条件(空白のみは不可・ExperiencePost.LESSON_MIN_LENGTH文字以上)で実際に有効な投稿
   * のみを対象にする(RewardService参照。投稿フォーム側のバリデーションが将来的に不具合を
   * 起こしても、ここで独立して教訓の有効性を再確認することで報酬の水増しを防ぐ)。
   */
  @Query(
      "select count(p) from ExperiencePost p where p.author.id = :authorId and p.status = :status"
          + " and p.lesson is not null and length(trim(p.lesson)) >= :minLessonLength")
  long countRewardEligibleByAuthorId(
      @Param("authorId") Long authorId,
      @Param("status") PostStatus status,
      @Param("minLessonLength") int minLessonLength);

  /** 投稿ボタン連打・通信再送による二重投稿防止(submissionToken)の冪等性チェックに使う。 */
  Optional<ExperiencePost> findBySubmissionTokenAndAuthorId(String submissionToken, Long authorId);

  long countByStatus(PostStatus status);

  long countByCreatedAtAfter(LocalDateTime since);

  @Query("select count(distinct p.author.id) from ExperiencePost p where p.createdAt >= :since")
  long countDistinctAuthorsSince(@Param("since") LocalDateTime since);

  List<ExperiencePost> findByAuthorId(Long authorId);

  List<ExperiencePost> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

  @EntityGraph(attributePaths = {"author", "category", "tags", "lifeEvents"})
  List<ExperiencePost> findTop500ByStatusOrderByCreatedAtDesc(PostStatus status);

  @EntityGraph(attributePaths = {"author", "category", "tags", "values"})
  List<ExperiencePost> findByCategorySlugAndStatusOrderByCreatedAtDesc(String slug, PostStatus status);

  /**
   * 管理者ユーザー一覧の表示用に、指定したユーザー群の投稿数・初回/最新投稿日時
   * (PUBLISHEDのみ)をまとめて1回で取得する(N+1回避)。絞り込み・並び替え自体は
   * AdminUserSpecifications/AdminUserRepositoryCustomImplの相関サブクエリで行うため、
   * これはあくまで1ページ分の表示専用。
   */
  @Query(
      "select p.author.id, count(p), min(p.createdAt), max(p.createdAt) from ExperiencePost p"
          + " where p.author.id in :userIds and p.status = :status group by p.author.id")
  List<Object[]> aggregatePostStatsByAuthorIds(
      @Param("userIds") Collection<Long> userIds, @Param("status") PostStatus status);
}
