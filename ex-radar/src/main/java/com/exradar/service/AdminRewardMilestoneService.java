package com.exradar.service;

import com.exradar.entity.PostRewardMilestone;
import com.exradar.exception.ResourceNotFoundException;
import com.exradar.repository.BenefitDefinitionRepository;
import com.exradar.repository.PostRewardMilestoneRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理者向け「投稿報酬設定」画面(/admin/reward-milestones)。必要投稿数・配布特典・
 * 繰り返し間隔・有効/無効・表示順をコード変更なしで運用できるようにする
 * (Javaコードへ具体的な投稿数をハードコードしない、という要件の管理者側の受け皿)。
 */
@Service
public class AdminRewardMilestoneService {
  private final PostRewardMilestoneRepository milestones;
  private final BenefitDefinitionRepository benefitDefinitions;

  public AdminRewardMilestoneService(
      PostRewardMilestoneRepository milestones, BenefitDefinitionRepository benefitDefinitions) {
    this.milestones = milestones;
    this.benefitDefinitions = benefitDefinitions;
  }

  @Transactional(readOnly = true)
  public List<PostRewardMilestone> list() {
    return milestones.findAllByOrderByDisplayOrderAscRequiredPostCountAsc();
  }

  @Transactional(readOnly = true)
  public PostRewardMilestone detail(Long id) {
    return milestones.findById(id).orElseThrow(() -> new ResourceNotFoundException("投稿報酬設定が見つかりません"));
  }

  @Transactional(readOnly = true)
  public List<com.exradar.entity.BenefitDefinition> benefitOptions() {
    return benefitDefinitions.findAll();
  }

  @Transactional
  public PostRewardMilestone create(
      int requiredPostCount, Long benefitDefinitionId, Integer repeatInterval, int displayOrder) {
    var definition =
        benefitDefinitions
            .findById(benefitDefinitionId)
            .orElseThrow(() -> new ResourceNotFoundException("特典が見つかりません"));
    try {
      return milestones.save(
          new PostRewardMilestone(requiredPostCount, definition, repeatInterval, displayOrder));
    } catch (DataIntegrityViolationException e) {
      throw new IllegalArgumentException("必要投稿数 " + requiredPostCount + " の設定は既に存在します");
    }
  }

  @Transactional
  public void update(Long id, int requiredPostCount, Long benefitDefinitionId, Integer repeatInterval, int displayOrder) {
    var milestone = detail(id);
    var definition =
        benefitDefinitions
            .findById(benefitDefinitionId)
            .orElseThrow(() -> new ResourceNotFoundException("特典が見つかりません"));
    try {
      milestone.update(requiredPostCount, definition, repeatInterval, displayOrder);
      milestones.saveAndFlush(milestone);
    } catch (DataIntegrityViolationException e) {
      throw new IllegalArgumentException("必要投稿数 " + requiredPostCount + " の設定は既に存在します");
    }
  }

  @Transactional
  public void activate(Long id) {
    detail(id).activate();
  }

  @Transactional
  public void deactivate(Long id) {
    detail(id).deactivate();
  }
}
