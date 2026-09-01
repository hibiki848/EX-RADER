package com.exradar.repository;

import com.exradar.entity.LifeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LifeEventRepository extends JpaRepository<LifeEvent, Long> {}
