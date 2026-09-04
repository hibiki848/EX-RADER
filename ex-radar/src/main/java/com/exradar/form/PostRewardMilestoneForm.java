package com.exradar.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PostRewardMilestoneForm {
  @NotNull(message = "必要投稿数を入力してください")
  @Min(value = 1, message = "必要投稿数は1以上で入力してください")
  private Integer requiredPostCount;

  @NotNull(message = "配布する特典を選択してください")
  private Long benefitDefinitionId;

  @Min(value = 1, message = "繰り返し間隔は1以上で入力してください(空欄なら1回のみ)")
  private Integer repeatInterval;

  private int displayOrder;

  public Integer getRequiredPostCount() {
    return requiredPostCount;
  }

  public void setRequiredPostCount(Integer v) {
    requiredPostCount = v;
  }

  public Long getBenefitDefinitionId() {
    return benefitDefinitionId;
  }

  public void setBenefitDefinitionId(Long v) {
    benefitDefinitionId = v;
  }

  public Integer getRepeatInterval() {
    return repeatInterval;
  }

  public void setRepeatInterval(Integer v) {
    repeatInterval = v;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(int v) {
    displayOrder = v;
  }
}
