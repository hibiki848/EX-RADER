package com.exradar.service;

import com.exradar.entity.*;
import com.exradar.exception.ResourceNotFoundException;
import com.exradar.form.DecisionMemoForm;
import com.exradar.repository.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionMemoService {
  private final DecisionMemoRepository memos;
  private final UserRepository users;
  private final PersonalValueRepository values;

  public DecisionMemoService(
      DecisionMemoRepository m, UserRepository u, PersonalValueRepository v) {
    memos = m;
    users = u;
    values = v;
  }

  @Transactional(readOnly = true)
  public List<DecisionMemo> list(String email) {
    return memos.findByUserIdOrderByUpdatedAtDesc(user(email).getId());
  }

  @Transactional(readOnly = true)
  public DecisionMemo get(Long id, String email) {
    return memos
        .findByIdAndUserId(id, user(email).getId())
        .orElseThrow(() -> new ResourceNotFoundException("意思決定メモが見つかりません"));
  }

  @Transactional
  public DecisionMemo save(Long id, DecisionMemoForm f, String email) {
    var user = user(email);
    var memo =
        id == null
            ? new DecisionMemo(user)
            : memos
                .findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("意思決定メモが見つかりません"));
    memo.update(
        f.getTitle(),
        f.getConcern(),
        f.getOptionsText(),
        f.getAnxieties(),
        f.getDesiredGain(),
        f.getMustNotLose(),
        f.getCanCompromise(),
        f.getCannotCompromise(),
        f.getInitialThoughts(),
        f.getDiscoveries(),
        f.getHelpfulLessons(),
        f.getCurrentThoughts(),
        values.findAllById(f.getValueIds()));
    return memos.save(memo);
  }

  @Transactional
  public void delete(Long id, String email) {
    memos.delete(get(id, email));
  }

  private User user(String email) {
    return users.findByEmailIgnoreCase(email).orElseThrow();
  }
}
