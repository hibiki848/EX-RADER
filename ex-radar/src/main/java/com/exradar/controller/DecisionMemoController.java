package com.exradar.controller;

import com.exradar.form.DecisionMemoForm;
import com.exradar.repository.PersonalValueRepository;
import com.exradar.service.DecisionMemoService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/decision-memos")
public class DecisionMemoController {
  private final DecisionMemoService service;
  private final PersonalValueRepository values;

  public DecisionMemoController(DecisionMemoService s, PersonalValueRepository v) {
    service = s;
    values = v;
  }

  @ModelAttribute
  void common(Model m) {
    m.addAttribute("personalValues", values.findAllByOrderByDisplayOrder());
  }

  @GetMapping
  public String list(Principal p, Model m) {
    m.addAttribute("memos", service.list(p.getName()));
    return "decision-memos/list";
  }

  @GetMapping("/new")
  public String create(Model m) {
    m.addAttribute("decisionMemoForm", new DecisionMemoForm());
    return form(m, null);
  }

  @GetMapping("/{id}")
  public String detail(@PathVariable Long id, Principal p, Model m) {
    m.addAttribute("memo", service.get(id, p.getName()));
    return "decision-memos/detail";
  }

  @GetMapping("/{id}/edit")
  public String edit(@PathVariable Long id, Principal p, Model m) {
    m.addAttribute("decisionMemoForm", DecisionMemoForm.from(service.get(id, p.getName())));
    return form(m, id);
  }

  @PostMapping
  public String createSave(
      @Valid @ModelAttribute DecisionMemoForm f, BindingResult br, Principal p, Model m) {
    if (br.hasErrors()) return form(m, null);
    return "redirect:/decision-memos/" + service.save(null, f, p.getName()).getId();
  }

  @PostMapping("/{id}")
  public String update(
      @PathVariable Long id,
      @Valid @ModelAttribute DecisionMemoForm f,
      BindingResult br,
      Principal p,
      Model m) {
    if (br.hasErrors()) return form(m, id);
    service.save(id, f, p.getName());
    return "redirect:/decision-memos/" + id;
  }

  @PostMapping("/{id}/delete")
  public String delete(@PathVariable Long id, Principal p) {
    service.delete(id, p.getName());
    return "redirect:/decision-memos";
  }

  private String form(Model m, Long id) {
    m.addAttribute("memoId", id);
    return "decision-memos/form";
  }
}
