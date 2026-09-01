package com.exradar.repository;

import com.exradar.entity.PersonalValue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalValueRepository extends JpaRepository<PersonalValue, Long> {
  List<PersonalValue> findAllByOrderByDisplayOrder();
}
