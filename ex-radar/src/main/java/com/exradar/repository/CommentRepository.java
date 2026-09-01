package com.exradar.repository;

import com.exradar.entity.Comment;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CommentRepository extends JpaRepository<Comment, Long> {
  @EntityGraph(attributePaths = "author")
  List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);

  void deleteByAuthorId(Long authorId);

  void deleteByPostId(Long postId);
}
