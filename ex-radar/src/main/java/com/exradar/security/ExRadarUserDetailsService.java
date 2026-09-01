package com.exradar.security;

import com.exradar.repository.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class ExRadarUserDetailsService implements UserDetailsService {
  private final UserRepository users;

  public ExRadarUserDetailsService(UserRepository users) {
    this.users = users;
  }

  public UserDetails loadUserByUsername(String email) {
    var u =
        users
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません"));
    return User.withUsername(u.getEmail())
        .password(u.getPassword())
        .roles(u.getRole().name())
        .disabled(u.isSuspended())
        .build();
  }
}
