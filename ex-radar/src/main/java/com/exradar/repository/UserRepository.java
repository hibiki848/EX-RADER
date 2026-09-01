package com.exradar.repository;

import com.exradar.entity.Role;
import com.exradar.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmailIgnoreCase(String email);

  @EntityGraph(attributePaths = "values")
  Optional<User> findWithValuesByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  long countByRole(Role role);

  long countBySuspendedTrue();

  long countByCreatedAtAfter(LocalDateTime since);

  java.util.List<User> findAllByOrderByCreatedAtDesc();
}
