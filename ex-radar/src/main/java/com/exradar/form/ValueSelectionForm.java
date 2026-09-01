package com.exradar.form;

import java.util.*;

public class ValueSelectionForm {
  private Set<Long> valueIds = new LinkedHashSet<>();

  public Set<Long> getValueIds() {
    return valueIds;
  }

  public void setValueIds(Set<Long> valueIds) {
    this.valueIds = valueIds == null ? new LinkedHashSet<>() : valueIds;
  }
}
