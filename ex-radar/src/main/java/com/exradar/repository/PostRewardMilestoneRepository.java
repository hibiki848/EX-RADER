package com.exradar.repository;

import com.exradar.entity.PostRewardMilestone;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRewardMilestoneRepository extends JpaRepository<PostRewardMilestone, Long> {
  @EntityGraph(attributePaths = "benefitDefinition")
  List<PostRewardMilestone> findByActiveTrueOrderByRequiredPostCountAsc();

  @EntityGraph(attributePaths = "benefitDefinition")
  List<PostRewardMilestone> findAllByOrderByDisplayOrderAscRequiredPostCountAsc();
}
