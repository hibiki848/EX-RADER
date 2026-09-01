package com.exradar.repository;

import com.exradar.entity.DecisionMemo;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DecisionMemoRepository extends JpaRepository<DecisionMemo, Long> {
  @EntityGraph(attributePaths = "values")
  List<DecisionMemo> findByUserIdOrderByUpdatedAtDesc(Long userId);

  @EntityGraph(attributePaths = "values")
  Optional<DecisionMemo> findByIdAndUserId(Long id, Long userId);

  void deleteByUserId(Long userId);
}
