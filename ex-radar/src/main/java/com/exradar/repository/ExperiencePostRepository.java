package com.exradar.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.exradar.entity.ExperiencePost;

import jakarta.persistence.LockModeType;

public interface ExperiencePostRepository
    extends JpaRepository<ExperiencePost, Long>, JpaSpecificationExecutor<ExperiencePost> {
  @Query(
      "select distinct p from ExperiencePost p join fetch p.author join fetch p.category left join"
          + " fetch p.lifeEvents left join fetch p.tags left join fetch p.values where p.id=:id")
  Optional<ExperiencePost> findDetailedById(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from ExperiencePost p join fetch p.author where p.id=:id")
  Optional<ExperiencePost> findForInteraction(@Param("id") Long id);

  @Override
  @EntityGraph(attributePaths = {"author", "category", "tags"})
  Page<ExperiencePost> findAll(Specification<ExperiencePost> specification, Pageable pageable);

  @EntityGraph(attributePaths = {"author", "category", "tags"})
  List<ExperiencePost> findTop6ByPublishedTrueOrderByCreatedAtDesc();

  @EntityGraph(attributePaths = {"author", "category", "tags"})
  List<ExperiencePost> findTop6ByPublishedTrueOrderBySatisfactionDescCreatedAtDesc();

  @EntityGraph(attributePaths = {"author", "category", "tags"})
  Page<ExperiencePost> findByAuthorIdAndPublishedTrue(Long authorId, Pageable pageable);

  @EntityGraph(attributePaths = {"category", "tags"})
  List<ExperiencePost> findByAuthorIdAndPublishedTrue(Long authorId);

  boolean existsByAuthorIdAndPublishedTrue(Long authorId);

  long countByPublishedTrue();

  long countByCreatedAtAfter(LocalDateTime since);

  @Query("select count(distinct p.author.id) from ExperiencePost p where p.createdAt >= :since")
  long countDistinctAuthorsSince(@Param("since") LocalDateTime since);

  java.util.List<ExperiencePost> findByAuthorId(Long authorId);

  List<ExperiencePost> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

  @EntityGraph(attributePaths = {"author", "category", "tags", "lifeEvents"})
  List<ExperiencePost> findTop500ByPublishedTrueOrderByCreatedAtDesc();

  @EntityGraph(attributePaths = {"author", "category", "tags", "values"})
  List<ExperiencePost> findByCategorySlugAndPublishedTrueOrderByCreatedAtDesc(String slug);
}
