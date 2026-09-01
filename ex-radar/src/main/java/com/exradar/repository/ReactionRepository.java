package com.exradar.repository;

import com.exradar.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {
  Optional<Reaction> findByUserIdAndPostIdAndType(Long userId, Long postId, ReactionType type);

  long countByPostIdAndType(Long postId, ReactionType type);

  List<Reaction> findByUserIdAndPostId(Long userId, Long postId);

  @EntityGraph(attributePaths = {"post"})
  List<Reaction> findByUserIdOrderByCreatedAtDesc(Long userId);

  void deleteByUserId(Long userId);

  void deleteByPostId(Long postId);
}
