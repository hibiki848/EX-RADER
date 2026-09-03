package com.exradar.repository;

import com.exradar.entity.ExperienceRead;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperienceReadRepository extends JpaRepository<ExperienceRead, Long> {
  Optional<ExperienceRead> findByUserIdAndPostId(Long userId, Long postId);

  boolean existsByUserIdAndPostId(Long userId, Long postId);

  /** 一覧カード表示用に、指定した投稿群のうち既読済みのIDだけをまとめて取得する(N+1回避)。 */
  @Query("select r.post.id from ExperienceRead r where r.user.id = :userId and r.post.id in :postIds")
  List<Long> findReadPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

  void deleteByUserId(Long userId);

  void deleteByPostId(Long postId);
}
