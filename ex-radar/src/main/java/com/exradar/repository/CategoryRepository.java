package com.exradar.repository;

import com.exradar.entity.Category;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
  Optional<Category> findBySlug(String slug);

  List<Category> findByActiveTrueOrderByDisplayOrder();
}
