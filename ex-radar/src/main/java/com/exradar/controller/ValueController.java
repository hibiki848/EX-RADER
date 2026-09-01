package com.exradar.controller;

import com.exradar.form.ValueSelectionForm;
import com.exradar.repository.*;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/mypage/values")
public class ValueController {
  private final UserRepository users;
  private final PersonalValueRepository values;

  public ValueController(UserRepository u, PersonalValueRepository v) {
    users = u;
    values = v;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public String form(Principal p, Model m) {
    var user = users.findByEmailIgnoreCase(p.getName()).orElseThrow();
    var form = new ValueSelectionForm();
    form.setValueIds(
        user.getValues().stream().map(v -> v.getId()).collect(java.util.stream.Collectors.toSet()));
    m.addAttribute("valueSelectionForm", form);
    m.addAttribute("personalValues", values.findAllByOrderByDisplayOrder());
    return "account/values";
  }

  @PostMapping
  @Transactional
  public String save(Principal p, @ModelAttribute ValueSelectionForm form) {
    users
        .findByEmailIgnoreCase(p.getName())
        .orElseThrow()
        .replaceValues(values.findAllById(form.getValueIds()));
    return "redirect:/mypage/values?saved";
  }
}
