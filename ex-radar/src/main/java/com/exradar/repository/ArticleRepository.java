package com.exradar.repository;

import com.exradar.entity.Article;
import com.exradar.entity.ArticleStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRepository extends JpaRepository<Article, Long> {
  Optional<Article> findBySlugAndStatus(String slug, ArticleStatus status);

  Optional<Article> findBySlug(String slug);

  List<Article> findByStatusOrderByPublishedAtDesc(ArticleStatus status);

  List<Article> findTop3ByStatusOrderByPublishedAtDesc(ArticleStatus status);

  List<Article> findAllByOrderByUpdatedAtDesc();

  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, Long id);
}
