package com.exradar.form;

import com.exradar.entity.ReportTargetType;
import jakarta.validation.constraints.*;

public class ReportForm {
  @NotNull(message = "通報対象を選択してください")
  private ReportTargetType targetType;

  @NotNull(message = "通報対象を選択してください")
  @Positive(message = "通報対象が不正です")
  private Long targetId;

  @NotBlank(message = "通報理由を入力してください")
  @Size(max = 1000, message = "通報理由は1000文字以内で入力してください")
  private String reason;

  public ReportTargetType getTargetType() {
    return targetType;
  }

  public void setTargetType(ReportTargetType v) {
    targetType = v;
  }

  public Long getTargetId() {
    return targetId;
  }

  public void setTargetId(Long v) {
    targetId = v;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String v) {
    reason = v;
  }
}
