package com.exradar.service;

import com.exradar.entity.*;
import com.exradar.exception.DuplicateEmailException;
import com.exradar.form.RegistrationForm;
import com.exradar.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserRepository users;
  private final PasswordEncoder encoder;

  public UserService(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  @Transactional
  public User register(RegistrationForm form) {
    if (!form.getPassword().equals(form.getPasswordConfirmation()))
      throw new IllegalArgumentException("確認用パスワードが一致しません");
    if (users.existsByEmailIgnoreCase(form.getEmail())) throw new DuplicateEmailException();
    return users.save(
        new User(
            form.getEmail().trim(),
            encoder.encode(form.getPassword()),
            form.getDisplayName().trim(),
            Role.USER));
  }
}
