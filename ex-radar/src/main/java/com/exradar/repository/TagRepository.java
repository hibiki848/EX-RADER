package com.exradar.repository;

import com.exradar.entity.Tag;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
  Optional<Tag> findByName(String name);

  Optional<Tag> findByNameIgnoreCase(String name);

  List<Tag> findTop30ByOrderByNameAsc();
}
