package com.exradar.service;

import static org.assertj.core.api.Assertions.*;

import com.exradar.entity.*;
import com.exradar.exception.ResourceNotFoundException;
import com.exradar.form.DecisionMemoForm;
import com.exradar.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DecisionMemoServiceTest {
  @Autowired DecisionMemoService service;
  @Autowired UserRepository users;
  @Autowired PersonalValueRepository values;

  @Test
  void savesPrivateMemoWithValuesAndOnlyOwnerCanReadIt() {
    users.save(new User("memo@example.com", "encoded", "メモ利用者", Role.USER));
    users.save(new User("other@example.com", "encoded", "別の利用者", Role.USER));
    var form = new DecisionMemoForm();
    form.setTitle("進学か就職か");
    form.setConcern("卒業後の進路に悩んでいる");
    form.setOptionsText("大学進学、高卒就職");
    form.setInitialThoughts("収入を優先したい");
    form.setValueIds(java.util.Set.of(values.findAllByOrderByDisplayOrder().getFirst().getId()));

    var saved = service.save(null, form, "memo@example.com");

    assertThat(service.get(saved.getId(), "memo@example.com").getValues()).hasSize(1);
    assertThatThrownBy(() -> service.get(saved.getId(), "other@example.com"))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
