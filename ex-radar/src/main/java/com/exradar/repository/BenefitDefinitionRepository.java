package com.exradar.repository;

import com.exradar.entity.BenefitDefinition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitDefinitionRepository extends JpaRepository<BenefitDefinition, Long> {
  Optional<BenefitDefinition> findByCode(String code);
}
